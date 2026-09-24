/**
 * 端到端冒烟测试。
 *
 * 前提：应用已启动，且 lifeline.old-platform.base-url 指向 tools/mock-old-platform.mjs
 *       （即 LIFELINE_OLD_BASE_URL=http://localhost:9999）。
 * 若不满足该前提，本脚本会把测试数据真实推送到所配置的老平台，因此默认先做一次连通性检查。
 *
 * 运行：node tools/smoke-test.mjs [baseUrl]
 */
const app = process.argv[2] || 'http://localhost:8080';
const old = process.env.MOCK_OLD_URL || 'http://localhost:9999';

let pass = 0;
let fail = 0;

function check(name, ok, detail) {
  if (ok) { pass++; console.log('  PASS  ' + name); }
  else { fail++; console.log('  FAIL  ' + name + (detail ? ' -> ' + detail : '')); }
}

async function json(method, path, body) {
  const res = await fetch(app + path, {
    method,
    headers: body ? { 'Content-Type': 'application/json; charset=utf-8' } : {},
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = { raw: text }; }
  return { status: res.status, data };
}

const COMPANY = '宜昌市联调测试燃气有限公司';

async function main() {
  console.log('目标应用: ' + app);
  console.log('老平台(模拟器): ' + old);

  // 安全闸：确认老平台是模拟器，避免把测试数据推到真实老平台
  try {
    const probe = await fetch(old + '/_mock/received');
    if (!probe.ok) throw new Error('HTTP ' + probe.status);
  } catch (e) {
    console.error('\n中止：老平台地址 ' + old + ' 不是 mock 模拟器（' + e.message + '）。');
    console.error('请用 LIFELINE_OLD_BASE_URL=' + old + ' 启动应用后再运行本脚本。');
    process.exit(2);
  }

  console.log('\n[1] 凭证登记与 UTF-8 往返');
  const reg = await json('POST', '/admin/api/credentials', {
    accessKey: 'mock-ak-0001',
    secretKey: 'mock-sk-0001',
    companyName: COMPANY,
    contact: '张工',
  });
  check('登记返回 200', reg.status === 200, 'status=' + reg.status);
  check('企业名 UTF-8 正确', reg.data?.companyName === COMPANY, JSON.stringify(reg.data?.companyName));
  check('只回显密钥尾号', reg.data?.secretHint === '****0001', String(reg.data?.secretHint));
  check('响应不含密钥明文', !JSON.stringify(reg.data || {}).includes('mock-sk-0001'));

  const list = await json('GET', '/admin/api/credentials');
  const found = (list.data || []).find(c => c.accessKey === 'mock-ak-0001');
  check('列表可查到该凭证', !!found);
  check('列表不含密文字段', !JSON.stringify(list.data || {}).includes('secretCipher'));
  const credId = found?.id;

  console.log('\n[2] 用老凭证向老平台验证');
  const verify = await json('POST', '/admin/api/credentials/' + credId + '/verify');
  check('验证成功', verify.status === 200 && verify.data?.ok === true, JSON.stringify(verify.data));

  console.log('\n[3] 接口清单');
  const cat = await json('GET', '/admin/api/interfaces');
  check('共 19 个条目', cat.data?.total === 19, 'total=' + cat.data?.total);
  check('其中 18 个业务操作', cat.data?.businessCount === 18, 'business=' + cat.data?.businessCount);
  const token = (cat.data?.entries || []).find(e => e.kind === 'TOKEN');
  check('令牌接口路径正确', token?.path === '/data-docking-api/token', token?.path);
  const reseq = (cat.data?.entries || []).filter(e => e.tag === 'risk');
  check('同 tag 不同 apiCmd 能区分（risk 有 2 条）', reseq.length === 2, 'count=' + reseq.length);

  console.log('\n[4] 调试台全链路：留存 -> 入库 -> 转发');
  const sample = await json('GET', '/admin/api/interfaces/9/sample?count=2');
  check('能生成报文模板', typeof sample.data?.body === 'string' && sample.data.body.includes('ycrqqy_realtime'));

  // 每次运行使用不同流水号，保证脚本可重复执行（否则会被上一轮的唯一键判重拦住）
  const runId = String(Date.now()).slice(-9);
  const bodyObj = JSON.parse(sample.data.body);
  for (const [i, item] of (bodyObj.apiBody.data || []).entries()) {
    if (item && typeof item === 'object' && 'lsh' in item) {
      item.lsh = '420500ycrqqy' + runId + i;
    }
  }
  const uniqueBody = JSON.stringify(bodyObj);

  const send = await json('POST', '/admin/api/debug/send', {
    accessKey: 'mock-ak-0001',
    body: uniqueBody,
  });
  check('老平台接受（code=0）', send.data?.status === 'ACCEPTED_OLD', JSON.stringify(send.data?.status));
  check('2 条数据全部入库', send.data?.storedCount === 2, 'stored=' + send.data?.storedCount);
  check('无重复', send.data?.duplicateCount === 0);
  check('路由已知', send.data?.routeKnown === true);
  const recordId = send.data?.recordId;

  console.log('\n[5] 重复流水号按唯一键判重');
  const resend = await json('POST', '/admin/api/debug/send', {
    accessKey: 'mock-ak-0001',
    body: uniqueBody,
  });
  check('重复提交被识别', resend.data?.duplicateCount === 2, 'dup=' + resend.data?.duplicateCount);
  check('重复提交不新增库存', resend.data?.storedCount === 0, 'stored=' + resend.data?.storedCount);

  console.log('\n[6] 老平台确实收到了原始数据');
  const mockState = await (await fetch(old + '/_mock/received')).json();
  check('模拟老平台收到至少 2 次转发', mockState.count >= 2, 'count=' + mockState.count);
  const last = mockState.received[mockState.received.length - 1];
  check('转发内容 tag 正确', last?.tag === 'ycrqqy_realtime', last?.tag);

  console.log('\n[7] 留存与数据项可查');
  const detail = await json('GET', '/admin/api/records/' + recordId);
  check('留存记录可查', detail.status === 200);
  check('留存保留了原始请求体', (detail.data?.record?.rawBody || '').includes('apiCmd'));
  check('数据项已落库', (detail.data?.items || []).length === 2, 'items=' + (detail.data?.items || []).length);
  check('数据项 URL 可读（非转义）', JSON.stringify(detail.data?.items || []).includes('420500'));

  console.log('\n[8] 调用者正式链路：取令牌 -> 推送');
  const ts = Math.floor(Date.now() / 1000);
  const crypto = await import('node:crypto');
  const signature = crypto.createHmac('sha256', 'mock-sk-0001')
    .update('accessKey=mock-ak-0001&timestamp=' + ts, 'utf8').digest('hex');
  const tokenRes = await fetch(app + '/data-docking-api/token?accessKey=mock-ak-0001&timestamp=' +
    ts + '&signature=' + signature, { method: 'POST' });
  const tokenBody = await tokenRes.json();
  check('取令牌成功', tokenRes.status === 200 && tokenBody.code === 0, JSON.stringify(tokenBody).slice(0, 200));
  check('返回的是新平台令牌', typeof tokenBody.data?.token === 'string' && tokenBody.data.token.length > 20);
  check('令牌不含 Bearer 前缀', !String(tokenBody.data?.token || '').startsWith('Bearer '));
  check('不返回老平台令牌', !JSON.stringify(tokenBody).includes('mock-old-token'));

  const pushBody = JSON.stringify({
    apiCmd: 'inspection_third_party_data_access',
    apiBody: { tag: 'district', operationType: 'I', data: [{ sszx: 'csaqzx_rq', name: '联调片区' }] },
  });
  const push = await fetch(app + '/data-docking-api/execute/api', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + tokenBody.data.token },
    body: pushBody,
  });
  const pushJson = await push.json();
  check('推送成功（HTTP 200）', push.status === 200, 'status=' + push.status);
  check('业务码 code=0', pushJson.code === 0, JSON.stringify(pushJson));

  console.log('\n[9] 非法令牌被拒');
  const bad = await fetch(app + '/data-docking-api/execute/api', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer not-a-real-token' },
    body: pushBody,
  });
  check('非法令牌返回 401', bad.status === 401, 'status=' + bad.status);

  console.log('\n[10] 签名错误被拒');
  const badSig = await fetch(app + '/data-docking-api/token?accessKey=mock-ak-0001&timestamp=' +
    Math.floor(Date.now() / 1000) + '&signature=deadbeef', { method: 'POST' });
  check('错误签名返回 401', badSig.status === 401, 'status=' + badSig.status);

  console.log('\n[11] 老平台业务失败 -> REJECTED_OLD');
  await fetch(old + '/_mock/mode', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ code: 1 }),
  });
  const failRunId = String(Date.now() + 1).slice(-9);
  const failBody = JSON.parse(sample.data.body);
  for (const [i, item] of (failBody.apiBody.data || []).entries()) {
    if (item && typeof item === 'object' && 'lsh' in item) item.lsh = '420500fail' + failRunId + i;
  }
  const failSend = await json('POST', '/admin/api/debug/send', {
    accessKey: 'mock-ak-0001', body: JSON.stringify(failBody),
  });
  check('状态为 REJECTED_OLD', failSend.data?.status === 'REJECTED_OLD', String(failSend.data?.status));
  check('数据仍已入库（失败不影响留存）', failSend.data?.storedCount === 2, 'stored=' + failSend.data?.storedCount);
  const failDetail = await json('GET', '/admin/api/records/' + failSend.data?.recordId);
  check('记录了老平台业务码 1', failDetail.data?.record?.oldPlatformCode === 1,
    'code=' + failDetail.data?.record?.oldPlatformCode);

  console.log('\n[12] 老平台超时 -> UNKNOWN（不得伪装成功）');
  await fetch(old + '/_mock/mode', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ timeout: true }),
  });
  const toRunId = String(Date.now() + 2).slice(-9);
  const toBody = JSON.parse(sample.data.body);
  for (const [i, item] of (toBody.apiBody.data || []).entries()) {
    if (item && typeof item === 'object' && 'lsh' in item) item.lsh = '420500timeout' + toRunId + i;
  }
  const toSend = await json('POST', '/admin/api/debug/send', {
    accessKey: 'mock-ak-0001', body: JSON.stringify(toBody),
  });
  check('状态为 UNKNOWN', toSend.data?.status === 'UNKNOWN', String(toSend.data?.status));
  check('返回给调用者的响应是失败', toSend.data?.response?.code === 1, JSON.stringify(toSend.data?.response));
  const toDetail = await json('GET', '/admin/api/records/' + toSend.data?.recordId);
  check('未记录业务码（未取得）', !toDetail.data?.record?.oldPlatformCode,
    'code=' + toDetail.data?.record?.oldPlatformCode);

  console.log('\n[13] 超时时调用者侧返回 502 而非 200');
  const toPush = await fetch(app + '/data-docking-api/execute/api', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + tokenBody.data.token },
    body: JSON.stringify({
      apiCmd: 'lifeline_data_batch_acces',
      apiBody: { tag: 'ycrqqy_realtime', data: [{ lsh: '420500tpush' + toRunId, sszx: 'csaqzx_rq' }] },
    }),
  });
  check('HTTP 状态为 502', toPush.status === 502, 'status=' + toPush.status);
  const toPushJson = await toPush.json();
  check('业务码为 1（不伪装成功）', toPushJson.code === 1, JSON.stringify(toPushJson));

  // 恢复正常模式
  await fetch(old + '/_mock/mode', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({}),
  });

  console.log('\n============================');
  console.log('通过 ' + pass + ' 项，失败 ' + fail + ' 项');
  process.exit(fail === 0 ? 0 : 1);
}

main().catch(e => { console.error('冒烟测试异常：', e); process.exit(1); });
