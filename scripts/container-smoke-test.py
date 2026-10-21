#!/usr/bin/env python3
"""
容器化部署冒烟测试（仅标准库）。
覆盖：JWT 登录 -> 下单 -> 发起支付 -> 支付回调 -> MQ 消费确认库存 ->
      商家接单 -> 用户取消退款 -> 库存/支付/退款终态校验。

依赖：docker CLI 可访问 solo-eats-mysql；应用已通过 docker compose 启动。
用法：python scripts/container-smoke-test.py
"""
import hashlib
import hmac
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE = os.environ.get("SMOKE_BASE_URL", "http://127.0.0.1:8080")
USER_ID = 1
MERCHANT_ID = 10
SHOP_ID = 1
SKU_ID = 1001


def load_env():
    env = {}
    path = os.path.join(ROOT, ".env")
    if not os.path.exists(path):
        return env
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            env[key.strip()] = value.strip()
    return env


ENV = load_env()


def setting(key, default):
    return ENV.get(key) or os.environ.get(key) or default


MYSQL_USER = setting("MYSQL_USER", "solo")
MYSQL_DATABASE = setting("MYSQL_DATABASE", "solo_eats")
MYSQL_CONTAINER = setting("MYSQL_CONTAINER", "solo-eats-mysql")


def http(method, path, body=None, token=None, idempotency_key=None, expected=200):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    request = urllib.request.Request(BASE + path, data=data, method=method)
    request.add_header("Content-Type", "application/json")
    if token:
        request.add_header("Authorization", "Bearer " + token)
    if idempotency_key:
        request.add_header("Idempotency-Key", idempotency_key)
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            status = response.status
            raw = response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        status = error.code
        raw = error.read().decode("utf-8")
    if status != expected:
        raise AssertionError(f"{method} {path} -> HTTP {status}: {raw}")
    return json.loads(raw) if raw else {}


def login(role, **identity):
    payload = {"role": role, **identity}
    data = http("POST", "/api/v1/auth/login", payload)["data"]
    return data["token"]


def mysql_scalar(sql):
    password = setting("MYSQL_PASSWORD", "")
    if not password:
        raise RuntimeError("MYSQL_PASSWORD 未在 .env 或环境变量中设置")
    # 允许通过 MYSQL_DATABASE/MYSQL_USER 覆盖演示库配置
    sql = sql.replace("solo_eats.", MYSQL_DATABASE + ".")
    # 通过容器环境变量传密码，避免宿主机 ps 看到命令行明文
    command = [
        "docker", "exec", "-e", "MYSQL_PWD=" + password, MYSQL_CONTAINER,
        "mysql", "-u" + MYSQL_USER, "-N", "-e", sql,
    ]
    result = subprocess.check_output(command, text=True, stderr=subprocess.DEVNULL)
    return result.strip()


def wait_order_status(order_id, expected, timeout_seconds=20):
    deadline = time.time() + timeout_seconds
    actual = None
    while time.time() < deadline:
        actual = int(mysql_scalar(f"SELECT status FROM solo_eats.t_order WHERE id = {order_id}"))
        if actual == expected:
            return actual
        time.sleep(1)
    raise AssertionError(f"order {order_id} status={actual}, expected {expected}")


def payment_sign(secret, payment_no, trade_no, amount):
    content = f"{payment_no}:{trade_no}:{amount}".encode("utf-8")
    return hmac.new(secret.encode("utf-8"), content, hashlib.sha256).hexdigest()


def main():
    print("[1/8] 探针检查")
    health = http("GET", "/healthz")
    assert health["status"] == "UP", health
    ready = http("GET", "/readyz")
    assert ready["status"] == "UP", ready

    print("[2/8] JWT 登录（用户 / 商家）")
    user_token = login("C_USER", userId=USER_ID)
    merchant_token = login("MERCHANT", merchantId=MERCHANT_ID, shopId=SHOP_ID)
    assert user_token and merchant_token

    print("[3/8] 创建配送订单")
    order = http(
        "POST", "/api/v1/orders",
        {
            "userId": USER_ID,
            "merchantId": MERCHANT_ID,
            "shopId": SHOP_ID,
            "fulfillmentType": 1,
            "packagingFee": 100,
            "deliveryFee": 300,
            "receiverName": "张三",
            "receiverPhone": "13800000000",
            "receiverAddress": "幸福路 1 号",
            "remark": "container smoke",
            "items": [{"skuId": SKU_ID, "quantity": 1}],
        },
        token=user_token,
        idempotency_key=uuid.uuid4().hex,
    )["data"]
    order_id = order["orderId"]
    assert order["status"] == 0, order
    assert order["payAmount"] == 3200, order
    assert int(mysql_scalar(
        f"SELECT available_qty FROM solo_eats.t_sku_stock WHERE shop_id={SHOP_ID} AND sku_id={SKU_ID}"
    )) == 99
    print(f"      orderId={order_id} payAmount={order['payAmount']}")

    print("[4/8] 发起支付")
    payment = http(
        "POST", "/api/v1/payments/pay",
        {"orderId": order_id, "channel": 1},
        token=user_token,
        idempotency_key=uuid.uuid4().hex,
    )["data"]
    assert payment["status"] == 10, payment

    print("[5/8] 支付回调（HMAC-SHA256）")
    trade_no = "SMOKE-" + uuid.uuid4().hex[:12]
    secret = ENV.get("PAYMENT_CALLBACK_SECRET") or os.environ.get("PAYMENT_CALLBACK_SECRET")
    if not secret:
        raise RuntimeError("PAYMENT_CALLBACK_SECRET 未设置")
    sign = payment_sign(secret, payment["paymentNo"], trade_no, payment["amount"])
    http("POST", "/api/v1/payments/callback", {
        "paymentNo": payment["paymentNo"],
        "tradeNo": trade_no,
        "amount": payment["amount"],
        "sign": sign,
    })

    print("[6/8] 等待 MQ 消费并校验库存确认")
    wait_order_status(order_id, 10)
    assert int(mysql_scalar(
        f"SELECT status FROM solo_eats.t_payment_order WHERE id={payment['paymentId']}"
    )) == 20
    assert int(mysql_scalar(
        f"SELECT sold_qty FROM solo_eats.t_sku_stock WHERE shop_id={SHOP_ID} AND sku_id={SKU_ID}"
    )) == 1
    assert mysql_scalar(
        f"SELECT COUNT(*) FROM solo_eats.t_delivery_order WHERE order_id={order_id}"
    ) == "1"

    print("[7/8] 商家接单")
    accepted = http(
        "POST", f"/api/v1/orders/{order_id}/accept",
        token=merchant_token,
    )["data"]
    assert accepted["status"] == 20, accepted

    print("[8/8] 用户取消并全额退款")
    cancelled = http(
        "POST", f"/api/v1/orders/{order_id}/cancel",
        {"reason": "容器冒烟取消", "operatorType": 1},
        token=user_token,
        idempotency_key=uuid.uuid4().hex,
    )["data"]
    assert cancelled["orderStatus"] == 70, cancelled
    assert cancelled["refundAmount"] == 3200, cancelled

    assert int(mysql_scalar(
        f"SELECT status FROM solo_eats.t_order WHERE id={order_id}"
    )) == 70
    assert int(mysql_scalar(
        f"SELECT status FROM solo_eats.t_payment_order WHERE id={payment['paymentId']}"
    )) == 60
    assert mysql_scalar(
        f"SELECT status FROM solo_eats.t_refund_order WHERE order_id={order_id}"
    ) == "30"
    stock = mysql_scalar(
        f"SELECT CONCAT(available_qty, '/', locked_qty, '/', sold_qty) "
        f"FROM solo_eats.t_sku_stock WHERE shop_id={SHOP_ID} AND sku_id={SKU_ID}"
    )
    assert stock == "100/0/0", stock
    assert http("GET", "/readyz")["status"] == "UP"

    print("SMOKE OK")
    print(json.dumps({
        "orderId": order_id,
        "paymentNo": payment["paymentNo"],
        "refundNo": cancelled["refundNo"],
        "stock": stock,
    }, ensure_ascii=False))


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"SMOKE FAILED: {exc}", file=sys.stderr)
        sys.exit(1)
