#!/usr/bin/env bash
set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

MYSQL_PASSWORD="$(grep -E '^MYSQL_PASSWORD=' .env | head -1 | cut -d= -f2- | tr -d '\r')"

if [ -z "${MYSQL_PASSWORD:-}" ]; then
  echo "MYSQL_PASSWORD not set in .env"
  exit 1
fi

TS="$(date +%s)"
JAR="solo-eats-app/target/solo-eats-app-1.0.0-SNAPSHOT.jar"

mysql_q() {
  docker exec -i solo-eats-mysql mysql -usolo -p"$MYSQL_PASSWORD" "$@"
}

cleanup_data() {
  mysql_q solo_eats <<'SQL' 2>/dev/null
DELETE FROM t_refund_item WHERE refund_id IN (
  SELECT id FROM t_refund_order WHERE order_id IN (
    SELECT id FROM t_order WHERE remark IN ('refund test','reject test','cancel test')
  )
);
DELETE FROM t_refund_order WHERE order_id IN (
  SELECT id FROM t_order WHERE remark IN ('refund test','reject test','cancel test')
);
DELETE FROM t_rider_income WHERE order_id IN (
  SELECT id FROM t_order WHERE remark IN ('refund test','reject test','cancel test')
);
DELETE FROM t_payment_order WHERE order_id IN (
  SELECT id FROM t_order WHERE remark IN ('refund test','reject test','cancel test')
);
DELETE FROM t_delivery_order WHERE order_id IN (
  SELECT id FROM t_order WHERE remark IN ('refund test','reject test','cancel test')
);
DELETE FROM t_stock_log WHERE biz_id IN (
  SELECT id FROM t_order WHERE remark IN ('refund test','reject test','cancel test')
);
DELETE FROM t_stock_lock WHERE order_id IN (
  SELECT id FROM t_order WHERE remark IN ('refund test','reject test','cancel test')
);
DELETE FROM t_order_discount WHERE order_id IN (
  SELECT id FROM t_order WHERE remark IN ('refund test','reject test','cancel test')
);
DELETE FROM t_order_item WHERE order_id IN (
  SELECT id FROM t_order WHERE remark IN ('refund test','reject test','cancel test')
);
DELETE FROM t_order_status_log WHERE order_id IN (
  SELECT id FROM t_order WHERE remark IN ('refund test','reject test','cancel test')
);
DELETE FROM t_order WHERE remark IN ('refund test','reject test','cancel test');
SQL
}

create_order() {
  local body_file="$1"
  local key="$2"
  local out_file="$3"
  curl -sS -o "$out_file" -w "create HTTP=%{http_code}\n" >&2 \
    -X POST http://localhost:8080/api/v1/orders \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: $key" \
    --data-binary @"$body_file"
  node -e "const fs=require('fs');const r=JSON.parse(fs.readFileSync(process.argv[1],'utf8'));process.stdout.write(String(r.data?r.data.orderId:''))" "$out_file"
}

pay_and_callback() {
  local order_id="$1"
  local tag="$2"
  local pay_file="/tmp/pay-$tag.json"
  curl -sS -o "$pay_file" -w "pay HTTP=%{http_code}\n" >&2 \
    -X POST http://localhost:8080/api/v1/payments/pay \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: pay-$tag-$TS" \
    -d "{\"orderId\":$order_id,\"channel\":3}"

  local pay_no amount trade sign
  pay_no=$(node -e "const fs=require('fs');const r=JSON.parse(fs.readFileSync(process.argv[1],'utf8'));process.stdout.write(String(r.data?r.data.paymentNo:''))" "$pay_file")
  amount=$(node -e "const fs=require('fs');const r=JSON.parse(fs.readFileSync(process.argv[1],'utf8'));process.stdout.write(String(r.data?r.data.amount:''))" "$pay_file")
  trade="TRADE-$tag-$TS"
  sign=$(node -e "const c=require('crypto');process.stdout.write(c.createHmac('sha256',process.argv[1]).update(process.argv[2]+':'+process.argv[3]+':'+process.argv[4]).digest('hex'))" "test-secret-123" "$pay_no" "$trade" "$amount")

  curl -sS -o "/tmp/callback-$tag.json" -w "callback HTTP=%{http_code}\n" >&2 \
    -X POST http://localhost:8080/api/v1/payments/callback \
    -H "Content-Type: application/json" \
    -d "{\"paymentNo\":\"$pay_no\",\"tradeNo\":\"$trade\",\"amount\":$amount,\"sign\":\"$sign\"}"

  local order_status=""
  for _ in $(seq 1 20); do
    order_status=$(mysql_q -N -e "SELECT status FROM solo_eats.t_order WHERE id=$order_id" 2>/dev/null | tr -d ' \r\n')
    if [ "$order_status" = "10" ]; then break; fi
    sleep 1
  done
  echo "order=$order_id pay_status=$order_status payment=$pay_no" >&2
}

if jps -l | grep -q 'solo-eats-app-1.0.0-SNAPSHOT.jar'; then
  echo "solo-eats-app is already running; stop it first"
  exit 1
fi

echo "=== pre-clean and reset ==="
cleanup_data
docker exec -i solo-eats-mysql mysql -usolo -p"$MYSQL_PASSWORD" solo_eats \
  < solo-eats-app/src/main/resources/db/migration/V2__sku_and_demo_data.sql 2>/dev/null

echo "=== start app ==="
java -jar "$JAR" --app.order.timeout-delay-seconds=600 --app.payment.callback-secret=test-secret-123 \
  > /tmp/solo-eats-refund-test.log 2>&1 &
for _ in $(seq 1 30); do
  if curl -s --max-time 2 http://localhost:8080/actuator/health | grep -q '"status":"UP"'; then
    break
  fi
  sleep 1
done
curl -s --max-time 3 http://localhost:8080/actuator/health; echo

echo "=== scenario 1: paid order + user refund ==="
cat > /tmp/order-a.json <<'JSON'
{"userId":1,"merchantId":1,"shopId":1,"fulfillmentType":2,"couponId":3001,"packagingFee":200,"deliveryFee":0,"pickupPhone":"13800000000","remark":"refund test","items":[{"skuId":1001,"quantity":2}]}
JSON
A_ID=$(create_order /tmp/order-a.json "refund-a-order-$TS" /tmp/order-a-resp.json)
echo "A_ID=$A_ID"
pay_and_callback "$A_ID" "refund-a"

curl -sS -o /tmp/refund-a-resp.json -w "refund HTTP=%{http_code}\n" \
  -X POST "http://localhost:8080/api/v1/orders/$A_ID/refund" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: refund-a-$TS" \
  -d '{"reason":"user changed mind"}'
cat /tmp/refund-a-resp.json; echo

echo "--- duplicate refund with same key ---"
curl -sS -w "\nHTTP=%{http_code}\n" \
  -X POST "http://localhost:8080/api/v1/orders/$A_ID/refund" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: refund-a-$TS" \
  -d '{"reason":"user changed mind"}'

echo "--- scenario 1 DB ---"
mysql_q -e "
SELECT id,status,payment_status,refund_status,refunded_amount,pay_amount
FROM solo_eats.t_order WHERE id=$A_ID;
SELECT payment_no,status FROM solo_eats.t_payment_order WHERE order_id=$A_ID;
SELECT refund_no,order_id,refund_type,amount,status,channel_refund_no
FROM solo_eats.t_refund_order WHERE order_id=$A_ID;
SELECT order_item_id,qty,refund_amount,restock_flag
FROM solo_eats.t_refund_item
WHERE refund_id=(SELECT id FROM solo_eats.t_refund_order WHERE order_id=$A_ID ORDER BY id DESC LIMIT 1);
SELECT sku_id,available_qty,locked_qty,sold_qty FROM solo_eats.t_sku_stock WHERE sku_id=1001;
SELECT id,status,order_id FROM solo_eats.t_user_coupon WHERE id=3001;
SELECT biz_type,biz_id,sku_id,op,qty,before_available,after_available
FROM solo_eats.t_stock_log WHERE biz_id=$A_ID ORDER BY id;
SELECT order_id,from_status,to_status,event
FROM solo_eats.t_order_status_log WHERE order_id=$A_ID ORDER BY id;
" 2>/dev/null

echo "=== scenario 2: paid order + merchant reject ==="
cat > /tmp/order-b.json <<'JSON'
{"userId":1,"merchantId":1,"shopId":1,"fulfillmentType":2,"packagingFee":200,"deliveryFee":0,"pickupPhone":"13800000000","remark":"reject test","items":[{"skuId":1001,"quantity":2}]}
JSON
B_ID=$(create_order /tmp/order-b.json "reject-b-order-$TS" /tmp/order-b-resp.json)
echo "B_ID=$B_ID"
pay_and_callback "$B_ID" "reject-b"

curl -sS -o /tmp/reject-b-resp.json -w "reject HTTP=%{http_code}\n" \
  -X POST "http://localhost:8080/api/v1/orders/$B_ID/reject" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: reject-b-$TS" \
  -d '{"reason":"out of stock","merchantId":1}'
cat /tmp/reject-b-resp.json; echo

echo "--- scenario 2 DB ---"
mysql_q -e "
SELECT id,status,payment_status,refund_status,refunded_amount,pay_amount
FROM solo_eats.t_order WHERE id=$B_ID;
SELECT payment_no,status FROM solo_eats.t_payment_order WHERE order_id=$B_ID;
SELECT refund_no,order_id,amount,status FROM solo_eats.t_refund_order WHERE order_id=$B_ID;
SELECT sku_id,available_qty,locked_qty,sold_qty FROM solo_eats.t_sku_stock WHERE sku_id=1001;
SELECT order_id,from_status,to_status,event
FROM solo_eats.t_order_status_log WHERE order_id=$B_ID ORDER BY id;
" 2>/dev/null

echo "=== scenario 3: unpaid cancel ==="
cat > /tmp/order-c.json <<'JSON'
{"userId":1,"merchantId":1,"shopId":1,"fulfillmentType":2,"packagingFee":200,"deliveryFee":0,"pickupPhone":"13800000000","remark":"cancel test","items":[{"skuId":1001,"quantity":2}]}
JSON
C_ID=$(create_order /tmp/order-c.json "cancel-c-order-$TS" /tmp/order-c-resp.json)
echo "C_ID=$C_ID"

curl -sS -w "\nHTTP=%{http_code}\n" \
  -X POST "http://localhost:8080/api/v1/orders/$C_ID/cancel" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: cancel-c-$TS" \
  -d '{"reason":"user cancel unpaid","operatorType":1}'

echo "--- scenario 3 DB ---"
mysql_q -e "
SELECT id,status,payment_status,refund_status,refunded_amount FROM solo_eats.t_order WHERE id=$C_ID;
SELECT COUNT(*) AS refunds FROM solo_eats.t_refund_order WHERE order_id=$C_ID;
SELECT sku_id,available_qty,locked_qty,sold_qty FROM solo_eats.t_sku_stock WHERE sku_id=1001;
SELECT order_id,from_status,to_status,event
FROM solo_eats.t_order_status_log WHERE order_id=$C_ID ORDER BY id;
" 2>/dev/null

echo "=== log errors ==="
grep -n 'ERROR\|Exception' /tmp/solo-eats-refund-test.log | head -20 || true

echo "=== cleanup and stop ==="
cleanup_data
docker exec -i solo-eats-mysql mysql -usolo -p"$MYSQL_PASSWORD" solo_eats \
  < solo-eats-app/src/main/resources/db/migration/V2__sku_and_demo_data.sql 2>/dev/null
for q in order.delay.queue order.timeout.queue order.pay.success.queue; do
  docker exec solo-eats-rabbitmq rabbitmqctl purge_queue "$q" >/dev/null 2>&1 || true
done
APP_PID=$(jps -l | grep 'solo-eats-app-1.0.0-SNAPSHOT.jar' | awk '{print $1}' | head -1)
if [ -n "$APP_PID" ]; then
  taskkill //F //PID "$APP_PID" >/dev/null 2>&1 || true
fi
echo "=== reset check ==="
mysql_q -N -e "
SELECT COUNT(*) FROM solo_eats.t_order;
SELECT COUNT(*) FROM solo_eats.t_payment_order;
SELECT COUNT(*) FROM solo_eats.t_refund_order;
SELECT sku_id,available_qty,locked_qty,sold_qty FROM solo_eats.t_sku_stock ORDER BY sku_id;
SELECT id,status,order_id FROM solo_eats.t_user_coupon WHERE id=3001;
" 2>/dev/null
