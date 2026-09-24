/**
 * 取令牌探测：只调用 /data-docking-api/token，不触碰任何业务接口。
 *
 * 目的：一次性确认源文档留下的三个阻塞项 ——
 *   1) 签名参数顺序与输出格式
 *   2) /token 参数位置（query 还是 form）
 *   3) /token 响应结构（是否含 data.token、令牌是否自带 Bearer 前缀）
 *
 * 运行（凭证只用环境变量传入，不落盘、不进仓库）：
 *   $env:LIFELINE_PROBE_BASE_URL   = 'http://111.47.65.236:9910'
 *   $env:LIFELINE_PROBE_ACCESS_KEY = '...'
 *   $env:LIFELINE_PROBE_SECRET_KEY = '...'
 *   node tools/probe-token.mjs
 */
import crypto from 'node:crypto';

const baseUrl = process.env.LIFELINE_PROBE_BASE_URL;
const accessKey = process.env.LIFELINE_PROBE_ACCESS_KEY;
const secretKey = process.env.LIFELINE_PROBE_SECRET_KEY;
if (!baseUrl || !accessKey || !secretKey) {
  console.error('缺少环境变量：LIFELINE_PROBE_BASE_URL / LIFELINE_PROBE_ACCESS_KEY / LIFELINE_PROBE_SECRET_KEY');
  process.exit(2);
}

const TOKEN_PATH = '/data-docking-api/token';
const url = baseUrl.replace(/\/$/, '') + TOKEN_PATH;

function mask(s) {
  if (!s || s.length < 12) return '***';
  return s.slice(0, 6) + '...' + s.slice(-4) + ' (len=' + s.length + ')';
}

function hmac(secret, text) {
  return crypto.createHmac('sha256', secret).update(text, 'utf8').digest('hex');
}

// 源文档只写了 key=value&key=value，未定义顺序，这里把两种常见写法都算出来
function signatures(ts) {
  return {
    'sorted(accessKey,timestamp)': hmac(secretKey, 'accessKey=' + accessKey + '&timestamp=' + ts),
    'given(timestamp,accessKey)': hmac(secretKey, 'timestamp=' + ts + '&accessKey=' + accessKey),
  };
}

async function call(label, opts) {
  const started = Date.now();
  try {
    const res = await fetch(opts.url, {
      method: 'POST',
      headers: opts.headers,
      body: opts.body,
      signal: AbortSignal.timeout(15000),
    });
    const text = await res.text();
    const ms = Date.now() - started;
    console.log('');
    console.log('--- ' + label);
    console.log('    HTTP ' + res.status + '  (' + ms + 'ms)');
    console.log('    body: ' + (text.length > 600 ? text.slice(0, 600) + '…' : text));
    return { status: res.status, text: text };
  } catch (e) {
    console.log('');
    console.log('--- ' + label);
    console.log('    请求失败: ' + e.message);
    return null;
  }
}

console.log('目标: ' + url);
console.log('accessKey: ' + mask(accessKey));
console.log('secretKey: ' + mask(secretKey));

const ts = String(Math.floor(Date.now() / 1000));
const sigs = signatures(ts);

// 0) 先打一个不存在的路径，确认服务确实在该端口上、且能区分 404
await call('0. 对照：不存在的路径（验证服务可达与 404 行为）', {
  url: baseUrl.replace(/\/$/, '') + '/data-docking-api/__probe_not_exist__',
  headers: { 'Content-Type': 'application/json' },
  body: '{}',
});

// 1) 完全不带参数，看错误信息如何描述必需参数
await call('1. 不带任何参数（query）', {
  url: url, headers: {}, body: undefined,
});

// 2) query + sorted 签名
const q = new URLSearchParams({ accessKey: accessKey, timestamp: ts, signature: sigs['sorted(accessKey,timestamp)'] });
await call('2. query + 签名顺序 accessKey,timestamp', {
  url: url + '?' + q.toString(), headers: {}, body: undefined,
});

// 3) query + 另一种顺序
const q2 = new URLSearchParams({ accessKey: accessKey, timestamp: ts, signature: sigs['given(timestamp,accessKey)'] });
await call('3. query + 签名顺序 timestamp,accessKey', {
  url: url + '?' + q2.toString(), headers: {}, body: undefined,
});
// 4) form + 两种顺序
await call('4. form + 签名顺序 accessKey,timestamp', {
  url: url,
  headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
  body: new URLSearchParams({ accessKey: accessKey, timestamp: ts, signature: sigs['sorted(accessKey,timestamp)'] }).toString(),
});
await call('5. form + 签名顺序 timestamp,accessKey', {
  url: url,
  headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
  body: new URLSearchParams({ accessKey: accessKey, timestamp: ts, signature: sigs['given(timestamp,accessKey)'] }).toString(),
});

console.log('');
console.log('说明：以上只调用了取令牌接口，未向任何业务接口发送数据。');
console.log('若全部失败且返回签名错误，说明签名原文格式与两种猜测都不同（可能含其他参数或不同拼接方式）。');