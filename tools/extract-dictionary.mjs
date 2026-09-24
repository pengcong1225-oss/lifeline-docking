/**
 * 从源文档《数据字典.md》提取与本平台 18 个业务接口相关的字典，生成知识库页面。
 *
 * 源文档共 93 类字典；本脚本只摘录接口字段实际会用到的那些，其余以索引形式列出。
 * 源文档更新后重新运行本脚本即可刷新。
 *
 * 运行：node tools/extract-dictionary.mjs [源文档路径]
 */
import fs from 'node:fs/promises';
import path from 'node:path';

const defaultSrc = 'E:/ai-work/docs/2026-09-23-city-lifeline-api-docs/artifacts/数据字典.md';
const src = process.argv[2] || process.env.LIFELINE_DICT_DOC || defaultSrc;
const outFile = path.join(import.meta.dirname, '..', 'docs', 'kb', '05-数据字典.md');

// ---------- 解析源文档 ----------
const text = await fs.readFile(src, 'utf8');
const dicts = [];
let group = '';
let cur = null;
for (const line of text.split(/\r?\n/)) {
  if (/^## /.test(line)) {
    const g = line.replace(/^## /, '').trim();
    if (g.indexOf('字典表') >= 0) group = g;
    continue;
  }
  const m = line.match(/^### (\d+)\.\s*(.+)$/);
  if (m) {
    cur = { num: Number(m[1]), name: m[2].trim(), group: group, headers: [], rows: [] };
    dicts.push(cur);
    continue;
  }
  if (!cur) continue;
  if (line.trim().charAt(0) !== '|') continue;
  const cells = line.split('|').slice(1, -1).map(function (c) { return c.trim(); });
  if (!cells.length) continue;
  if (cells.every(function (c) { return /^-+$/.test(c.replace(/:/g, '')); })) continue;
  if (!cur.headers.length) { cur.headers = cells; continue; }
  cur.rows.push(cells);
}

const byNum = function (n) { return dicts.filter(function (d) { return d.num === n; })[0]; };

function table(d) {
  const out = [];
  out.push('| ' + d.headers.join(' | ') + ' |');
  out.push('| ' + d.headers.map(function () { return '---'; }).join(' | ') + ' |');
  for (const r of d.rows) out.push('| ' + r.join(' | ') + ' |');
  return out.join('\n');
}

function section(title, d, note) {
  const out = [];
  out.push('### ' + title);
  if (note) out.push('');
  if (note) out.push(note);
  out.push('');
  out.push(table(d));
  out.push('');
  return out.join('\n');
}

// 只保留指定专项的行（用于大字典瘦身）
function filterSpecialty(d, wanted) {
  const i = d.headers.indexOf('专项');
  if (i < 0) return d;
  return {
    num: d.num, name: d.name, group: d.group, headers: d.headers,
    rows: d.rows.filter(function (r) { return wanted.indexOf(r[i]) >= 0; }),
  };
}

const PART = ['燃气', '燃气终端用户', '公共'];
const md = [];

md.push('# 数据字典');
md.push('');
md.push('> 来源：《省级城市生命线安全工程监管平台-数据对接规范（修订版本 V2.4）》2026-07-09 的《数据字典》，源文档共 **93 类**。');
md.push('>');
md.push('> 本页只摘录**本平台 18 个业务接口字段实际会用到的字典**；完整 93 类请查源文档。');
md.push('>');
md.push('> 由 `tools/extract-dictionary.mjs` 自动生成，**请勿手工编辑**；源文档更新后重新运行该脚本。');
md.push('');
md.push('## 一、编码类字典（必填字段直接引用）');
md.push('');
md.push('这三个字典决定 `dsbm`、`qhbm`、`sszx` 三个几乎每个接口都必填的字段，填错会被平台直接拒绝。');
md.push('');
md.push(section('1. 市州编码表 → 字段 `dsbm`', byNum(1)));
md.push('### 2. 区划编码表 → 字段 `qhbm`');
md.push('');
md.push('源文档共 103 行。本平台当前接入的是宜昌市燃气数据，下面只列宜昌市；其他地市请查源文档。');
md.push('');
const qu = byNum(2);
md.push(table({ headers: qu.headers, rows: qu.rows.filter(function (r) { return r[0].indexOf('宜昌') >= 0; }) }));
md.push('');
md.push(section('3. 专项类别表 → 字段 `sszx`', byNum(3)));
md.push('## 二、燃气业务相关字典');
md.push('');
md.push('本平台 18 个接口全部属于燃气专项（`csaqzx_rq`）及其相关专项，下面按业务域归类。');
md.push('');
md.push('### 风险与隐患');
md.push('');
md.push(section('风险等级', byNum(7)));
md.push(section('隐患类型（仅燃气 / 燃气终端用户 / 公共）', filterSpecialty(byNum(8), PART), '源文档共 136 行，覆盖全部专项。'));
md.push(section('隐患等级', byNum(9)));
md.push(section('隐患来源', byNum(10)));
md.push(section('整改状态', byNum(11)));
md.push('### 监测设备与指标');
md.push('');
md.push(section('监测设备类型（仅燃气相关）', filterSpecialty(byNum(13), PART), '源文档共 57 行，覆盖全部专项。'));
const zb = byNum(14);
md.push('### 监测指标字典表 → 字段 `jczb`');
md.push('');
md.push('源文档共 198 行，表头为「指标编号 / 专项 / 监测设备类型 / 监测指标 / 数值单位或枚举说明」。下面只列燃气相关。');
md.push('');
const zbHeader = zb.rows[0];
const zbBody = zb.rows.slice(1).filter(function (r) { return r[1] === '燃气'; });
md.push(table({ headers: zbHeader, rows: zbBody }));
md.push('');
md.push(section('设备运行状态', byNum(15)));
md.push(section('设备运维状态', byNum(16)));
md.push(section('监测设备监管方式', byNum(12)));
md.push('### 报警与预警');
md.push('');
md.push(section('报警级别', byNum(17)));
md.push(section('报警分类', byNum(93)));
md.push(section('预警来源', byNum(18)));
md.push(section('预警类型（仅燃气相关）', filterSpecialty(byNum(19), PART), '源文档共 22 行。'));
md.push(section('预警级别', byNum(20)));
md.push(section('预警处置状态', byNum(21)));
md.push('### 燃气用户与供气');
md.push('');
md.push(section('燃气用户类型', byNum(75)));
md.push(section('燃气类型', byNum(76)));
md.push(section('燃气用户标签', byNum(77)));
md.push(section('用户状态', byNum(88)));
md.push(section('用气性质', byNum(89)));
md.push(section('供气方式', byNum(90)));
md.push('### 点位与监测对象');
md.push('');
md.push(section('点位类型', byNum(91)));
md.push(section('监测管线', byNum(92)));
md.push(section('数据状态', byNum(24)));
md.push('## 三、其余字典索引（共 93 类）');
md.push('');
const included = [1, 2, 3, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 24, 75, 76, 77, 88, 89, 90, 91, 92, 93];
md.push('下表列出源文档全部 ' + dicts.length + ' 类字典。「已摘录」列标注本页是否已收录。');
md.push('');
md.push('| # | 所属分组 | 字典名称 | 条目数 | 已摘录 |');
md.push('| ---: | --- | --- | ---: | :---: |');
for (const d of dicts) {
  const got = included.indexOf(d.num) >= 0 ? '是' : '';
  md.push('| ' + d.num + ' | ' + d.group + ' | ' + d.name + ' | ' + d.rows.length + ' | ' + got + ' |');
}
md.push('');
md.push('未摘录的字典主要属于供水、排水、桥梁、瓶装液化气、消防等专项，本平台当前 18 个接口用不到。');
md.push('');

await fs.mkdir(path.dirname(outFile), { recursive: true });
await fs.writeFile(outFile, md.join('\n') + '\n', 'utf8');
console.log('已生成 ' + outFile);
console.log('摘录字典 ' + included.length + ' 类，索引列出 ' + dicts.length + ' 类');