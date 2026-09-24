/**
 * 从源文档《接口字段明细.md》生成接口清单 docking-api-catalog.json。
 *
 * 接口定义来自 Apifox 分享文档，不是运行时从老平台拉取；源文档更新后需要重新生成本文件。
 *
 * 运行：node tools/build-catalog.mjs [源文档路径]
 * 默认源文档取自 LIFELINE_FIELD_DOC 或仓库外的交接资料目录。
 */
import fs from 'node:fs/promises';
import path from 'node:path';

const defaultDoc = 'E:\\ai-work\\docs\\2026-09-23-city-lifeline-api-docs\\artifacts\\接口字段明细.md';
const docPath = process.argv[2] || process.env.LIFELINE_FIELD_DOC || defaultDoc;
const outPath = path.join(import.meta.dirname, '..', 'src', 'main', 'resources', 'docking-api-catalog.json');

// 调试台预填用的时间戳基准（示例值，不是真实时间）
const SAMPLE_EPOCH_MS = Date.parse('2026-09-23T10:00:00+08:00');

/**
 * 生成预填示例值。
 *
 * 只做“类型正确 + 明显是占位”的填充，不猜业务含义；发送前必须人工替换为真实测试数据。
 */
function sampleFor(name, type, desc) {
  // 源文档里写明的示例优先
  const ex = desc.match(/示例[:：]\s*([^；;，,。]+)/);

  const exact = {
    dsbm: '420500',   // 宜昌市，见市州编码表
    qhbm: '420502',   // 见区划编码表
    sjly: 'ycrqqy',   // 数据来源：拼音首字母简称
    sjtbzt: 'I',
    sszx: 'csaqzx_rq', // 所属专项：燃气
  };
  if (exact[name] !== undefined) return exact[name];
  if (name === 'lsh' || name.endsWith('Lsh')) return '420500ycrqqy2026092300001';

  if (type === 'object') {
    // 源文档只标了 object，未给结构，留空对象并保持占位，避免凭空编造字段。
    return {};
  }
  if (type === 'object[]' || type === 'array') return [];

  if (type === 'integer' || type === 'int32') {
    // 时间戳类字段填 0 会被平台理解成 1970 年，这里给毫秒时间戳
    if (/时间戳|timestamp/i.test(desc)) return SAMPLE_EPOCH_MS;
    return 0;
  }
  if (type === 'number') {
    if (name === 'lon' || name === 'jd') return 111.2865;
    if (name === 'lat' || name === 'wd') return 30.6919;
    return 0;
  }

  if (ex) return ex[1].trim();
  if (/JSONString|JSON字符串/i.test(desc)) return '{}';
  if (/格式 date-time/.test(desc)) return '2026-09-23 10:00:00';
  if (/格式 date\b/.test(desc)) return '2026-09-23';
  if (/电话|手机|联系方式/.test(desc) || /dh$/.test(name)) return '13800000000';
  if (/姓名/.test(desc) || /xm$/.test(name)) return '张三';
  if (/编号|编码/.test(desc)) return 'TEST-' + name.toUpperCase();
  return '待填写';
}

function parseTable(block) {
  const rows = [];
  // 源文档用 \| 转义单元格内部的竖线（如 sjly 的正则里就有）。
  // 必须先保护再按 | 拆分，否则描述会被截断、列也会错位。
  const PIPE = String.fromCharCode(1);
  for (const line of block.split('\n')) {
    if (!line.trim().startsWith('|')) continue;
    const cells = line.replace(/\\\|/g, PIPE).split('|').map(c => c.trim().split(PIPE).join('|'));
    if (cells.length < 5) continue;
    const [, a, b, c, d] = cells;
    if (a === '字段路径' || a === '名称' || /^-+$/.test(a.replace(/:/g, ''))) continue;
    rows.push({ path: a, type: b, required: c, desc: d });
  }
  return rows;
}

const text = await fs.readFile(docPath, 'utf8');
const sections = text.split(/\n## /).slice(1);
const entries = [];

for (const sec of sections) {
  const header = sec.split('\n')[0].trim();
  const m = header.match(/^(\d+)\.\s*(.+)$/);
  if (!m) continue;
  const seq = Number(m[1]);
  const parts = m[2].split(' / ');
  const category = parts.length > 1 ? parts[0] : '';
  const name = parts.length > 1 ? parts.slice(1).join(' / ') : m[2];

  const grab = (re) => { const mm = sec.match(re); return mm ? mm[1].trim() : null; };
  const apiCmd = grab(/-\s*`apiCmd`：`([^`]+)`/);
  const tag = grab(/-\s*`apiBody\.tag`：`([^`]+)`/);
  const operationType = grab(/-\s*`apiBody\.operationType`：`([^`]+)`/);
  const reqPath = grab(/-\s*请求：`[A-Z]+\s+([^`]+)`/) ?? '';
  const contentType = grab(/-\s*请求体：`([^`]+)`/) ?? '';

  const tables = {};
  for (const sub of sec.split(/\n### /).slice(1)) {
    tables[sub.split('\n')[0].trim()] = parseTable(sub);
  }

  const isToken = apiCmd === null && /token/i.test(reqPath);
  const dataFields = (tables['请求字段'] ?? [])
    .filter(f => f.path.includes('data[].'))
    .map(f => {
      const shortName = f.path.split('data[].')[1];
      return {
        name: shortName,
        type: f.type,
        required: f.required === '是',
        desc: f.desc,
        sample: sampleFor(shortName, f.type, f.desc),
      };
    });

  entries.push({
    seq, category, name,
    method: 'POST',
    path: reqPath,
    contentType,
    apiCmd, tag, operationType,
    kind: isToken ? 'TOKEN' : 'BUSINESS',
    queryParams: (tables['query 参数'] ?? []).map(f => ({
      name: f.path, type: f.type, required: f.required === '是', desc: f.desc,
    })),
    dataFields,
    responseFields: (tables['HTTP 200 响应'] ?? []).map(f => ({
      name: f.path, type: f.type, required: f.required === '是', desc: f.desc,
    })),
  });
}

const business = entries.filter(e => e.kind === 'BUSINESS');
if (entries.length !== 19 || business.length !== 18) {
  throw new Error('解析结果异常：期望 19 个条目 / 18 个业务操作，实际 ' +
    entries.length + ' / ' + business.length + '，请检查源文档格式是否变化');
}

const snapshot = {
  source: 'Apifox 分享文档 2026-09-23',
  generatedAt: '2026-09-23',
  sourceDoc: docPath,
  entries,
};

await fs.writeFile(outPath, JSON.stringify(snapshot, null, 2) + '\n', 'utf8');
console.log('已生成 ' + outPath);
console.log('条目 ' + entries.length + ' 个（业务 ' + business.length + ' 个）');
for (const e of entries) {
  console.log('  ' + String(e.seq).padStart(2) + '  ' +
    (e.kind === 'TOKEN' ? '(令牌)' : (e.apiCmd + ' / ' + e.tag + (e.operationType ? ' / ' + e.operationType : ''))) +
    '  fields=' + e.dataFields.length);
}
