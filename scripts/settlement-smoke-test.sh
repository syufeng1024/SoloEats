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
DELETE FROM t_settlement_item WHERE bill_id IN (
  SELECT id FROM t_settlement_bill WHERE merchant_id = 1
);
DELETE FROM t_settlement_bill WHERE merchant_id = 1;
DELETE FROM t_refund_item WHERE refund_id IN (
  SELECT id FROM t_refund_order WHERE order_id IN (
    SELECT id FROM t_order WHERE remark IN ('settlement test','settlement refunded')
  )
);
DELETE FROM t_refund_order WHERE order_id IN (
  SELECT id FROM t_order WHERE remark IN ('settlement test','settlement refunded')
);
DELETE FROM t_rider_income WHERE order_id IN (
  SELECT id FROM t_order WHERE remark IN ('settlement test','settlement refunded')
);
DELETE FROM t_payment_order WHERE order_id IN (
  SELECT id FROM t_order WHERE remark IN ('settlement test','settlement refunded')
);
DELETE FROM t_delivery_order WHERE order_id IN (
  SELECT id FROM t_order WHERE remark IN ('settlement test','settlement refunded')
);
DELETE FROM t_stock_log WHERE biz_id IN (
  SELECT id FROM t_order WHERE remark IN ('settlement test','settlement refunded')
);
DELETE FROM t_stock_lock WHERE order_id IN (
  SELECT id FROM t_order WHERE remark IN ('settlement test','settlement refunded')
);
DELETE FROM t_order_discount WHERE order_id IN (
  SELECT id FROM t_order WHERE remark IN ('settlement test','settlement refunded')
);
DELETE FROM t_order_item WHERE order_id IN (
  SELECT id FROM t_order WHERE remark IN ('settlement test','settlement refunded')
);
DELETE FROM t_order_status_log WHERE order_id IN (
  SELECT id FROM t_order WHERE remark IN ('settlement test','settlement refunded')
);
DELETE FROM t_order WHERE remark IN ('settlement test','settlement refunded');
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
  local pay_file="/tmp/settle-pay-$tag.json"
  curl -sS -o "$pay_file" -w "pay HTTP=%{http_code}\n" >&2 \
    -X POST http://localhost:8080/api/v1/payments/pay \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: settle-pay-$tag-$TS" \
    -d "{\"orderId\":$order_id,\"channel\":3}"

  local pay_no amount trade sign
  pay_no=$(node -e "const fs=require('fs');const r=JSON.parse(fs.readFileSync(process.argv[1],'utf8'));process.stdout.write(String(r.data?r.data.paymentNo:''))" "$pay_file")
  amount=$(node -e "const fs=require('fs');const r=JSON.parse(fs.readFileSync(process.argv[1],'utf8'));process.stdout.write(String(r.data?r.data.amount:''))" "$pay_file")
  trade="TRADE-$tag-$TS"
  sign=$(node -e "const c=require('crypto');process.stdout.write(c.createHmac('sha256',process.argv[1]).update(process.argv[2]+':'+process.argv[3]+':'+process.argv[4]).digest('hex'))" "test-secret-123" "$pay_no" "$trade" "$amount")

  curl -sS -o "/tmp/settle-callback-$tag.json" -w "callback HTTP=%{http_code}\n" >&2 \
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
  > /tmp/solo-eats-settlement-test.log 2>&1 &
for _ in $(seq 1 30); do
  if curl -s --max-time 2 http://localhost:8080/actuator/health | grep -q '"status":"UP"'; then
    break
  fi
  sleep 1
done
curl -s --max-time 3 http://localhost:8080/actuator/health; echo

echo "=== create completed settlement order (merchant coupon 3003) ==="
cat > /tmp/settle-order-1.json <<'JSON'
{"userId":1,"merchantId":1,"shopId":1,"fulfillmentType":1,"couponId":3003,"packagingFee":200,"deliveryFee":500,"receiverName":"Zhang San","receiverPhone":"13800000000","receiverAddress":"Test Road 1","remark":"settlement test","items":[{"skuId":1001,"quantity":2}]}
JSON
O1=$(create_order /tmp/settle-order-1.json "settle-order-1-$TS" /tmp/settle-order-1-resp.json)
echo "O1=$O1"
pay_and_callback "$O1" "settle-o1"

curl -sS -X POST "http://localhost:8080/api/v1/orders/$O1/accept" > /tmp/settle-accept.json
curl -sS -X POST "http://localhost:8080/api/v1/orders/$O1/prepared" > /tmp/settle-prepared.json
curl -sS -X POST "http://localhost:8080/api/v1/orders/$O1/rider/accept" -H "Content-Type: application/json" -d '{"riderId":9001}' > /tmp/settle-rider-accept.json
curl -sS -X POST "http://localhost:8080/api/v1/orders/$O1/rider/pickup" -H "Content-Type: application/json" -d '{"riderId":9001}' > /tmp/settle-rider-pickup.json
curl -sS -X POST "http://localhost:8080/api/v1/orders/$O1/rider/delivered" -H "Content-Type: application/json" -d '{"riderId":9001}' > /tmp/settle-rider-delivered.json
cat /tmp/settle-rider-delivered.json; echo
mysql_q -e "UPDATE solo_eats.t_order SET settle_eligible_at = DATE_SUB(NOW(3), INTERVAL 1 DAY) WHERE id=$O1;" 2>/dev/null

echo "=== create completed but refunded order (excluded) ==="
cat > /tmp/settle-order-2.json <<'JSON'
{"userId":1,"merchantId":1,"shopId":1,"fulfillmentType":2,"packagingFee":200,"deliveryFee":0,"pickupPhone":"13800000000","remark":"settlement refunded","items":[{"skuId":1001,"quantity":1}]}
JSON
O2=$(create_order /tmp/settle-order-2.json "settle-order-2-$TS" /tmp/settle-order-2-resp.json)
echo "O2=$O2"
pay_and_callback "$O2" "settle-o2"
mysql_q -e "UPDATE solo_eats.t_order SET status=60, refund_status=2, refunded_amount=pay_amount, completed_at=NOW(3), settle_eligible_at=DATE_SUB(NOW(3), INTERVAL 1 DAY) WHERE id=$O2;" 2>/dev/null

echo "=== trigger settlement ==="
curl -sS -w "\nHTTP=%{http_code}\n" -X POST http://localhost:8080/api/v1/admin/settlements/generate

echo "=== merchant bill list ==="
curl -sS -o /tmp/settle-list.json "http://localhost:8080/api/v1/merchant/settlements?merchantId=1"
cat /tmp/settle-list.json; echo
BILL_ID=$(node -e "const fs=require('fs');const r=JSON.parse(fs.readFileSync(process.argv[1],'utf8'));process.stdout.write(String(r.data&&r.data.bills&&r.data.bills[0]?r.data.bills[0].billId:''))" /tmp/settle-list.json)
echo "BILL_ID=$BILL_ID"

echo "=== merchant bill detail ==="
curl -sS -w "\nHTTP=%{http_code}\n" "http://localhost:8080/api/v1/merchant/settlements/$BILL_ID?merchantId=1"

echo "=== DB settlement ==="
mysql_q -e "
SELECT id,order_no,status,settlement_status,refund_status,refunded_amount,goods_amount,packaging_fee,delivery_fee,merchant_discount,platform_discount,pay_amount
FROM solo_eats.t_order WHERE id IN ($O1,$O2);
SELECT id,bill_no,merchant_id,bill_date,gross_amount,merchant_discount,platform_discount,commission_base,commission_amount,performance_fee,refund_amount,payable_amount,status
FROM solo_eats.t_settlement_bill WHERE id=$BILL_ID;
SELECT order_id,item_type,amount,remark FROM solo_eats.t_settlement_item WHERE bill_id=$BILL_ID ORDER BY id;
SELECT SUM(amount) AS item_sum FROM solo_eats.t_settlement_item WHERE bill_id=$BILL_ID;
" 2>/dev/null

echo "=== identity check ==="
node -e "
const fs=require('fs');
const list=JSON.parse(fs.readFileSync(process.argv[1],'utf8'));
const bill=list.data.bills[0];
console.log('merchantAmount+platformCommission=', bill.merchantAmount + bill.platformCommission);
console.log('bill.gross - merchantDiscount + platformDiscount=', bill.grossAmount - bill.merchantDiscount + bill.platformDiscount);
" /tmp/settle-list.json
mysql_q -N -e "
SELECT CONCAT('user_pay-delivery=', pay_amount - delivery_fee) FROM solo_eats.t_order WHERE id=$O1;
SELECT CONCAT('merchant+commission=', payable_amount + commission_amount) FROM solo_eats.t_settlement_bill WHERE id=$BILL_ID;
" 2>/dev/null

echo "=== log errors ==="
grep -n 'ERROR\|Exception' /tmp/solo-eats-settlement-test.log | head -20 || true

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
SELECT COUNT(*) FROM solo_eats.t_settlement_bill;
SELECT COUNT(*) FROM solo_eats.t_settlement_item;
SELECT sku_id,available_qty,locked_qty,sold_qty FROM solo_eats.t_sku_stock ORDER BY sku_id;
SELECT id,status,order_id FROM solo_eats.t_user_coupon WHERE id IN (3001,3003);
" 2>/dev/null
