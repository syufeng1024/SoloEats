// 临时 WS 联调脚本：验证握手、心跳、订单状态推送
const token = process.argv[2];
const url = `ws://localhost:8080/ws?token=${encodeURIComponent(token)}`;
const log = (m) => console.log(`[${new Date().toISOString().slice(11, 23)}] ${m}`);

const ws = new WebSocket(url);

ws.onopen = () => {
  log('OPEN 握手成功');
  ws.send('ping');
  log('SENT ping');
};

ws.onmessage = (e) => log('RECV ' + (typeof e.data === 'string' ? e.data : '[binary]'));
ws.onerror = (e) => log('ERROR ' + (e.message || 'ws error'));
ws.onclose = (e) => log(`CLOSED code=${e.code} reason=${e.reason || '-'}`);

setTimeout(() => { log('--- 结束 ---'); ws.close(); }, Number(process.argv[3] || 12000));
