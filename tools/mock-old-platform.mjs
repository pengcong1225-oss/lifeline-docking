/**
 * 老平台模拟器 —— 仅供本机联调验证，不要用于生产。
 *
 * 模拟老平台的两个端点：
 *   POST /data-docking-api/token         按签名规则校验后返回令牌
 *   POST /data-docking-api/execute/api   按 apiCmd 回执，可注入业务失败
 *
 * 启动：node tools/mock-old-platform.mjs [port]
 * 让新平台指向它：set LIFELINE_OLD_BASE_URL=http://localhost:9999
 *
 * 注入失败：请求头 X-Mock-Code: 1 可强制返回业务失败 code=1。
 * 注入超时：请求头 X-Mock-Timeout: 1 时不响应，用于验证“结果未知”分支。
 */
import http from 'node:http';
import crypto from 'node:crypto';

const port = Number(process.argv[2] || 9999);

// 模拟老平台已分配给企业的凭证。真实 secretKey 不写进仓库，这里只用于本机联调。
const CREDENTIALS = new Map([
  ['mock-ak-0001', 'mock-sk-0001'],
]);

const received = [];

// 全局行为开关，用于验证失败与超时分支；通过 POST /_mock/mode 设置。
let globalMode = { code: null, timeout: false };

function hmac(secret, text) {
  return crypto.createHmac('sha256', secret).update(text, 'utf8').digest('hex');
}

function readBody(req) {
  return new Promise((resolve) => {
    let data = '';
    req.on('data', (c) => { data += c; });
    req.on('end', () => resolve(data));
  });
}

function json(res, status, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(body);
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');
  console.log(new Date().toISOString(), req.method, url.pathname, url.search || '');

  if (url.pathname === '/data-docking-api/token') {
    // 兼容 query 与 form 两种参数位置（源文档两种说法都存在）
    let params = Object.fromEntries(url.searchParams.entries());
    if (req.method === 'POST') {
      const raw = await readBody(req);
      if (raw) params = { ...Object.fromEntries(new URLSearchParams(raw).entries()), ...params };
    }
    const { accessKey, timestamp, signature } = params;
    const secret = CREDENTIALS.get(accessKey);
    if (!secret) {
      return json(res, 401, { code: 1, msg: '未知 accessKey' });
    }
    // 与源文档一致的校验顺序：accessKey=..&timestamp=..
    const expected = hmac(secret, 'accessKey=' + accessKey + '&timestamp=' + timestamp);
    if (expected !== signature) {
      return json(res, 401, {
        code: 1, msg: '签名校验失败',
        _debug: { expected, received: signature },
      });
    }
    return json(res, 200, {
      code: 0,
      msg: '成功',
      data: {
        // 故意带上 Bearer 前缀，模拟源文档示例，验证新平台能剥离
        token: 'Bearer mock-old-token-' + Date.now(),
        issuedAt: new Date().toISOString(),
        expiresAt: new Date(Date.now() + 7200_000).toISOString(),
      },
    });
  }

  if (url.pathname === '/data-docking-api/execute/api') {
    const auth = req.headers['authorization'] || '';
    if (!auth.startsWith('Bearer mock-old-token-')) {
      return json(res, 401, { code: 1, msg: '令牌无效' });
    }
    const raw = await readBody(req);
    if (globalMode.timeout || req.headers['x-mock-timeout'] === '1') {
      return; // 故意不响应，触发调用方“结果未知”
    }
    let body = {};
    try { body = JSON.parse(raw); } catch (e) { /* 保持空对象 */ }
    received.push({ at: new Date().toISOString(), apiCmd: body.apiCmd, tag: body.apiBody?.tag, raw });
    const forced = req.headers['x-mock-code'];
    const code = forced !== undefined ? Number(forced)
      : (globalMode.code !== null ? globalMode.code : 0);
    return json(res, 200, {
      code,
      msg: code === 0 ? '成功' : '模拟业务失败',
      data: code === 0 ? true : null,
    });
  }

  if (url.pathname === '/_mock/received') {
    return json(res, 200, { count: received.length, received });
  }

  // 控制后续 execute 的行为：{"code":1} 业务失败，{"timeout":true} 不响应，{} 恢复正常
  if (url.pathname === '/_mock/mode' && req.method === 'POST') {
    const raw = await readBody(req);
    let m = {};
    try { m = JSON.parse(raw || '{}'); } catch (e) { /* 忽略 */ }
    globalMode = { code: m.code === undefined ? null : m.code, timeout: !!m.timeout };
    console.log('模式切换:', JSON.stringify(globalMode));
    return json(res, 200, globalMode);
  }

  return json(res, 404, { code: 1, msg: 'not found' });
});

server.listen(port, () => {
  console.log('老平台模拟器已启动: http://localhost:' + port);
  console.log('可用凭证: mock-ak-0001 / mock-sk-0001');
});
