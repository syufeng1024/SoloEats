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
BASE="http://localhost:8080"

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
    SELECT id FROM t_order WHERE remark IN ('auth test','trace test','rate test')
  )
);
DELETE FROM t_refund_order WHERE order_id IN (
  SELECT id FROM t_order WHERE remark IN ('auth test','trace test','rate test')
);
DELETE FROM t_rider_income WHERE order_id IN (
  SELECT id FROM t_order WHERE remark IN ('auth test','trace test','rate test')
);
DELETE FROM t_payment_order WHERE order_id IN (
  SELECT id FROM t_order WHERE remark IN ('auth test','trace test','rate test')
);
DELETE FROM t_delivery_order WHERE order_id IN (
  SELECT id FROM t_order WHERE remark IN ('auth test','trace test','rate test')
);
DELETE FROM t_stock_log WHERE biz_id IN (
  SELECT id FROM t_order WHERE remark IN ('auth test','trace test','rate test')
);
DELETE FROM t_stock_lock WHERE order_id IN (
  SELECT id FROM t_order WHERE remark IN ('auth test','trace test','rate test')
);
DELETE FROM t_order_discount WHERE order_id IN (
  SELECT id FROM t_order WHERE remark IN ('auth test','trace test','rate test')
);
DELETE FROM t_order_item WHERE order_id IN (
  SELECT id FROM t_order WHERE remark IN ('auth test','trace test','rate test')
);
DELETE FROM t_order_status_log WHERE order_id IN (
  SELECT id FROM t_order WHERE remark IN ('auth test','trace test','rate test')
);
DELETE FROM t_order WHERE remark IN ('auth test','trace test','rate test');
SQL
}

login() {
  local role="$1"
  local body="$2"
  local out="$3"
  curl -sS -o "$out" -w "login $role HTTP=%{http_code}\n" >&2 \
    -X POST "$BASE/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d "$body"
  node -e "const fs=require('fs');const r=JSON.parse(fs.readFileSync(process.argv[1],'utf8'));process.stdout.write(String(r.data?r.data.token:''))" "$out"
}

create_order() {
  local token="$1"
  local body_file="$2"
  local key="$3"
  local out="$4"
  local trace="${5:-}"
  if [ -n "$trace" ]; then
    curl -sS -o "$out" -w "order HTTP=%{http_code}\n" >&2 \
      -X POST "$BASE/api/v1/orders" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer $token" \
      -H "Idempotency-Key: $key" \
      -H "X-Trace-Id: $trace" \
      --data-binary @"$body_file"
  else
    curl -sS -o "$out" -w "order HTTP=%{http_code}\n" >&2 \
      -X POST "$BASE/api/v1/orders" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer $token" \
      -H "Idempotency-Key: $key" \
      --data-binary @"$body_file"
  fi
  node -e "const fs=require('fs');const r=JSON.parse(fs.readFileSync(process.argv[1],'utf8'));process.stdout.write(String(r.data?r.data.orderId:''))" "$out"
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
java -jar "$JAR" \
  --app.order.timeout-delay-seconds=3 \
  --app.rate-limit.order-create.limit=3 \
  --app.payment.callback-secret=test-secret-123 \
  > /tmp/solo-eats-security-test.log 2>&1 &
for _ in $(seq 1 30); do
  if curl -s --max-time 2 "$BASE/actuator/health" | grep -q '"status":"UP"'; then
    break
  fi
  sleep 1
done
curl -s --max-time 3 "$BASE/actuator/health"; echo

echo "=== login tokens ==="
USER1=$(login C_USER '{"role":"C_USER","userId":1}' /tmp/sec-login-user1.json)
USER2=$(login C_USER '{"role":"C_USER","userId":2}' /tmp/sec-login-user2.json)
MERCHANT1=$(login MERCHANT '{"role":"MERCHANT","merchantId":1,"shopId":1}' /tmp/sec-login-merchant1.json)
MERCHANT2=$(login MERCHANT '{"role":"MERCHANT","merchantId":2}' /tmp/sec-login-merchant2.json)
RIDER=$(login RIDER '{"role":"RIDER","riderId":9001}' /tmp/sec-login-rider.json)
ADMIN=$(login ADMIN '{"role":"ADMIN","userId":999}' /tmp/sec-login-admin.json)

echo "=== create auth test order ==="
cat > /tmp/sec-order-auth.json <<'JSON'
{"userId":1,"merchantId":1,"shopId":1,"fulfillmentType":2,"packagingFee":0,"deliveryFee":0,"pickupPhone":"13800000000","remark":"auth test","items":[{"skuId":1001,"quantity":1}]}
JSON
AUTH_ORDER=$(create_order "$USER1" /tmp/sec-order-auth.json "auth-order-$TS" /tmp/sec-order-auth-resp.json)
echo "AUTH_ORDER=$AUTH_ORDER"

echo "=== unauthorized access checks ==="
NO_TOKEN_LIST=$(curl -sS -o /tmp/sec-no-token.json -w "%{http_code}" "$BASE/api/v1/merchant/settlements?merchantId=1")
USER_ACCEPT=$(curl -sS -o /tmp/sec-user-accept.json -w "%{http_code}" -X POST "$BASE/api/v1/orders/$AUTH_ORDER/accept" -H "Authorization: Bearer $USER1")
MERCHANT2_ACCEPT=$(curl -sS -o /tmp/sec-m2-accept.json -w "%{http_code}" -X POST "$BASE/api/v1/orders/$AUTH_ORDER/accept" -H "Authorization: Bearer $MERCHANT2")
MERCHANT_REFUND=$(curl -sS -o /tmp/sec-m-refund.json -w "%{http_code}" -X POST "$BASE/api/v1/orders/$AUTH_ORDER/refund" -H "Authorization: Bearer $MERCHANT1" -H "Content-Type: application/json" -H "Idempotency-Key: role-refund-$TS" -d '{"reason":"role test"}')
RIDER_ACCEPT=$(curl -sS -o /tmp/sec-rider-accept.json -w "%{http_code}" -X POST "$BASE/api/v1/orders/$AUTH_ORDER/accept" -H "Authorization: Bearer $RIDER")
CUSER_RIDER=$(curl -sS -o /tmp/sec-user-rider.json -w "%{http_code}" -X POST "$BASE/api/v1/orders/$AUTH_ORDER/rider/accept" -H "Authorization: Bearer $USER1" -H "Content-Type: application/json" -d '{"riderId":9001}')
CUSER_ADMIN=$(curl -sS -o /tmp/sec-user-admin.json -w "%{http_code}" -X POST "$BASE/api/v1/admin/settlements/generate" -H "Authorization: Bearer $USER1")
ADMIN_ADMIN=$(curl -sS -o /tmp/sec-admin-admin.json -w "%{http_code}" -X POST "$BASE/api/v1/admin/settlements/generate" -H "Authorization: Bearer $ADMIN")
NO_TOKEN_CREATE=$(curl -sS -o /tmp/sec-no-token-create.json -w "%{http_code}" -X POST "$BASE/api/v1/orders" -H "Content-Type: application/json" -H "Idempotency-Key: no-token-$TS" --data-binary @/tmp/sec-order-auth.json)

echo "no_token_merchant_list=$NO_TOKEN_LIST"
echo "user_accept=$USER_ACCEPT"
echo "merchant2_accept=$MERCHANT2_ACCEPT"
echo "merchant_refund=$MERCHANT_REFUND"
echo "rider_accept=$RIDER_ACCEPT"
echo "cuser_rider_endpoint=$CUSER_RIDER"
echo "cuser_admin_generate=$CUSER_ADMIN"
echo "admin_generate=$ADMIN_ADMIN"
echo "no_token_create=$NO_TOKEN_CREATE"

echo "=== MQ TraceId check (timeout consumer) ==="
TRACE_MQ="trace-mq-$TS"
cat > /tmp/sec-order-trace.json <<'JSON'
{"userId":1,"merchantId":1,"shopId":1,"fulfillmentType":2,"packagingFee":0,"deliveryFee":0,"pickupPhone":"13800000000","remark":"trace test","items":[{"skuId":1001,"quantity":1}]}
JSON
TRACE_ORDER=$(create_order "$USER1" /tmp/sec-order-trace.json "trace-order-$TS" /tmp/sec-order-trace-resp.json "$TRACE_MQ")
echo "TRACE_ORDER=$TRACE_ORDER"
node -e "
const fs=require('fs');
const r=JSON.parse(fs.readFileSync(process.argv[1],'utf8'));
console.log('response traceId=', r.traceId, 'orderId=', r.data?r.data.orderId:null);
" /tmp/sec-order-trace-resp.json
sleep 7
echo "--- timeout consumer trace lines ---"
grep -n "consume order.timeout" /tmp/solo-eats-security-test.log | grep "$TRACE_MQ" || true

echo "=== Thread pool TraceId check ==="
TRACE_ASYNC="trace-async-$TS"
curl -sS -o /tmp/sec-trace-async.json -w "trace_async HTTP=%{http_code}\n" \
  "$BASE/api/v1/debug/trace/async" \
  -H "Authorization: Bearer $ADMIN" \
  -H "X-Trace-Id: $TRACE_ASYNC"
cat /tmp/sec-trace-async.json; echo
sleep 2
echo "--- async trace line ---"
grep -n "async trace probe" /tmp/solo-eats-security-test.log | grep "$TRACE_ASYNC" || true

echo "=== rate limit check (limit=3 per user) ==="
cat > /tmp/sec-order-rate.json <<'JSON'
{"userId":2,"merchantId":1,"shopId":1,"fulfillmentType":2,"packagingFee":0,"deliveryFee":0,"pickupPhone":"13800000000","remark":"rate test","items":[{"skuId":1001,"quantity":1}]}
JSON
for i in 1 2 3 4; do
  CODE=$(curl -sS -o "/tmp/sec-rate-$i.json" -w "%{http_code}" \
    -X POST "$BASE/api/v1/orders" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $USER2" \
    -H "Idempotency-Key: rate-$i-$TS" \
    --data-binary @/tmp/sec-order-rate.json)
  echo "rate_request_$i HTTP=$CODE body=$(cat /tmp/sec-rate-$i.json)"
done

echo "=== DB status ==="
mysql_q -e "
SELECT id,status,remark FROM solo_eats.t_order WHERE remark IN ('auth test','trace test','rate test') ORDER BY id;
" 2>/dev/null

echo "=== log errors ==="
grep -n 'ERROR\|Exception' /tmp/solo-eats-security-test.log | head -20 || true

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
SELECT sku_id,available_qty,locked_qty,sold_qty FROM solo_eats.t_sku_stock ORDER BY sku_id;
" 2>/dev/null
