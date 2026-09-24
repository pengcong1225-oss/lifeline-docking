/**
 * 知识库构建：
 *   1) 从 src/main/resources/docking-api-catalog.json 生成 docs/kb/02-接口清单.md
 *   2) 把 docs/kb/*.md 渲染成一个自包含的 index.html（无外部依赖，可离线打开）
 *
 * 运行：node tools/build-kb.mjs
 */
import fs from 'node:fs/promises';
import path from 'node:path';

const root = path.join(import.meta.dirname, '..');
const kbDir = path.join(root, 'docs', 'kb');
const catalogFile = path.join(root, 'src', 'main', 'resources', 'docking-api-catalog.json');
const outFile = path.join(root, 'src', 'main', 'resources', 'static', 'kb', 'index.html');

const ORDER = ['README.md', '01-接入总览.md', '02-接口清单.md', '03-路由规则与陷阱.md',
  '04-公共字段约定.md', '05-数据字典.md', '06-平台架构.md', '07-数据库设计.md',
  '08-部署与运行.md', '09-联调阻塞项.md', '10-验证记录.md', 'FAQ.md'];

const NL = String.fromCharCode(10);

// ---------- 1) 生成接口清单页 ----------

const catalog = JSON.parse(await fs.readFile(catalogFile, 'utf8'));
const entries = catalog.entries.slice().sort((a, b) => a.seq - b.seq);
const biz = entries.filter(e => e.kind !== 'TOKEN');
const token = entries.filter(e => e.kind === 'TOKEN')[0];

function routeKey(e) {
  let s = e.apiCmd + ' / ' + e.tag;
  if (e.operationType) s += ' / ' + e.operationType;
  return s;
}

function fieldTable(e) {
  const out = [];
  if (!e.dataFields.length) { out.push('（该条目没有数据字段）'); return out.join(NL); }
  out.push('| 字段 | 类型 | 必填 | 说明与约束 | 预填示例 |');
  out.push('| --- | --- | :---: | --- | --- |');
  for (const f of e.dataFields) {
    const sample = typeof f.sample === 'object' ? JSON.stringify(f.sample) : String(f.sample);
    // 源文档里的说明已经对竖线做了转义（\|），原样保留，重复转义或去转义都会破坏表格
    const desc = String(f.desc || '').split('|').join('\\|');
    out.push('| `' + f.name + '` | ' + f.type + ' | ' + (f.required ? '是' : '否') + ' | ' +
      (desc || '（源文档未给出说明）') + ' | `' + sample + '` |');
  }
  return out.join(NL);
}

const cl = [];
cl.push('# 接口清单');
cl.push('');
cl.push('> 本页由 `tools/build-kb.mjs` 从 `src/main/resources/docking-api-catalog.json` **自动生成**，请勿手工编辑。');
cl.push('>');
cl.push('> 接口定义来自源文档《接口字段明细》，**尚未经过与老平台的真实联调验证**。');
cl.push('');
cl.push('源文档共 **' + entries.length + ' 个条目**：1 个令牌接口 + ' + biz.length + ' 个业务操作。');
cl.push('');
cl.push('## 令牌接口');
cl.push('');
cl.push('`' + token.method + ' ' + token.path + '`');
cl.push('');
cl.push('| 参数 | 类型 | 必填 | 说明 |');
cl.push('| --- | --- | :---: | --- |');
for (const q of token.queryParams) {
  cl.push('| `' + q.name + '` | ' + q.type + ' | ' + (q.required ? '是' : '否') + ' | ' + (q.desc || '') + ' |');
}
cl.push('');
cl.push('> 参数位置存在文档冲突（接口页写 query、接入说明写 multipart/form-data），见[联调阻塞项](09-联调阻塞项.md)。');
cl.push('');
cl.push('## 业务操作一览');
cl.push('');
cl.push('全部 ' + biz.length + ' 个业务操作共用 `POST /data-docking-api/execute/api`，');
cl.push('靠 `apiCmd` + `apiBody.tag`（必要时加 `apiBody.operationType`）区分。');
cl.push('');
cl.push('| # | 业务类别 | 接口名称 | 路由键 | 字段数 |');
cl.push('| ---: | --- | --- | --- | ---: |');
for (const e of biz) {
  cl.push('| ' + e.seq + ' | ' + e.category + ' | ' + e.name + ' | `' + routeKey(e) + '` | ' + e.dataFields.length + ' |');
}
cl.push('');
cl.push('## 逐接口字段明细');
cl.push('');
cl.push('「预填示例」是调试台生成报文时使用的占位值，**不是真实数据**；发送前必须替换。');
cl.push('凡标注「源文档未给出说明」的字段，联调时需向平台方确认。');
cl.push('');
let lastCat = null;
for (const e of biz) {
  if (e.category !== lastCat) { cl.push('### ' + e.category); cl.push(''); lastCat = e.category; }
  cl.push('#### ' + e.seq + '. ' + e.name);
  cl.push('');
  cl.push('- 路由键：`' + routeKey(e) + '`');
  if (e.operationType) cl.push('- operationType：`' + e.operationType + '`');
  cl.push('- 字段数：' + e.dataFields.length);
  cl.push('');
  cl.push(fieldTable(e));
  cl.push('');
}
cl.push('---');
cl.push('');
cl.push('JSON 原始定义：`src/main/resources/docking-api-catalog.json`（生成工具 `tools/build-catalog.mjs`）。');
cl.push('');
await fs.writeFile(path.join(kbDir, '02-接口清单.md'), cl.join(NL), 'utf8');
console.log('已生成 02-接口清单.md（' + biz.length + ' 个业务操作）');
// ---------- 2) Markdown 渲染 ----------

function esc(s) {
  return String(s).split('&').join('&amp;').split('<').join('&lt;').split('>').join('&gt;');
}

// 与 GitHub 一致的标题锚点，供跨页 #fragment 链接使用
function slug(text) {
  return String(text).toLowerCase()
    .replace(/[^\p{L}\p{N}\s-]/gu, '')
    .trim()
    .replace(/\s/g, '-');
}

function inline(s) {
  let t = esc(s);
  const codes = [];
  const mark = String.fromCharCode(0);
  const codeRe = new RegExp('`' + '([^' + '`' + ']+)' + '`', 'g');
  t = t.replace(codeRe, function (m, c) { codes.push(c); return mark + (codes.length - 1) + mark; });
  t = t.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
  t = t.replace(/\[([^\]]+)\]\(([^)]+)\)/g, function (m, text, href) {
    let h = href;
    const mm = href.match(/^([^#]*\.md)(#.*)?$/);
    if (mm) h = (mm[2] || '#' + slug(mm[1].replace(/\.md$/, '')));
    return '<a href="' + h + '">' + text + '</a>';
  });
  const markRe = new RegExp(mark + '(\\d+)' + mark, 'g');
  t = t.replace(markRe, function (m, i) { return '<code>' + codes[Number(i)] + '</code>'; });
  return t;
}

function renderMarkdown(md) {
  const lines = md.split(/\r?\n/);
  const html = [];
  let i = 0;
  let para = [];
  function flushPara() {
    if (para.length) { html.push('<p>' + inline(para.join(' ')) + '</p>'); para = []; }
  }
  while (i < lines.length) {
    const line = lines[i];
    if (/^```/.test(line)) {
      flushPara();
      const lang = line.slice(3).trim();
      const buf = [];
      i++;
      while (i < lines.length && !/^```/.test(lines[i])) { buf.push(lines[i]); i++; }
      i++;
      html.push('<pre data-lang="' + esc(lang) + '"><code>' + esc(buf.join(NL)) + '</code></pre>');
      continue;
    }
    if (/^\|/.test(line) && i + 1 < lines.length && /^\|[\s:|-]+\|\s*$/.test(lines[i + 1])) {
      flushPara();
      const PIPE = String.fromCharCode(1);
      const splitRow = (l) => l.replace(/\\\|/g, PIPE).split('|').slice(1, -1).map(c => c.trim().split(PIPE).join('|'));
      const header = splitRow(line);
      i += 2;
      const rows = [];
      while (i < lines.length && /^\|/.test(lines[i])) {
        rows.push(splitRow(lines[i]));
        i++;
      }
      html.push('<div class="tw"><table><thead><tr>' +
        header.map(h => '<th>' + inline(h) + '</th>').join('') + '</tr></thead><tbody>' +
        rows.map(r => '<tr>' + r.map(c => '<td>' + inline(c) + '</td>').join('') + '</tr>').join('') +
        '</tbody></table></div>');
      continue;
    }
    const h = line.match(/^(#{1,6})\s+(.*)$/);
    if (h) {
      flushPara();
      const level = h[1].length;
      const text = h[2].trim();
      html.push('<h' + level + ' id="' + slug(text) + '">' + inline(text) + '</h' + level + '>');
      i++;
      continue;
    }
    if (/^---+\s*$/.test(line)) { flushPara(); html.push('<hr>'); i++; continue; }
    if (/^>/.test(line)) {
      flushPara();
      const buf = [];
      while (i < lines.length && /^>/.test(lines[i])) { buf.push(lines[i].replace(/^>\s?/, '')); i++; }
      html.push('<blockquote>' + renderMarkdown(buf.join(NL)) + '</blockquote>');
      continue;
    }
    if (/^\s*([-*]|\d+\.)\s+/.test(line)) {
      flushPara();
      const ordered = /^\s*\d+\.\s+/.test(line);
      const items = [];
      while (i < lines.length && /^\s*([-*]|\d+\.)\s+/.test(lines[i])) {
        items.push(lines[i].replace(/^\s*([-*]|\d+\.)\s+/, ''));
        i++;
        while (i < lines.length && lines[i].trim() &&
               !/^\s*([-*]|\d+\.)\s+/.test(lines[i]) &&
               !/^[#>|`]/.test(lines[i])) {
          items[items.length - 1] += ' ' + lines[i].trim();
          i++;
        }
      }
      const tag = ordered ? 'ol' : 'ul';
      html.push('<' + tag + '>' + items.map(t => '<li>' + inline(t) + '</li>').join('') + '</' + tag + '>');
      continue;
    }
    if (!line.trim()) { flushPara(); i++; continue; }
    para.push(line.trim());
    i++;
  }
  flushPara();
  return html.join(NL);
}

// ---------- 3) 组装单页 HTML ----------

const topics = [];
for (const file of ORDER) {
  let md;
  try { md = await fs.readFile(path.join(kbDir, file), 'utf8'); }
  catch (e) { console.warn('跳过缺失的主题：' + file); continue; }
  const id = file.replace(/\.md$/, '');
  const body = md.replace(/^#\s+.*\n/, '');
  const tm = md.match(/^#\s+(.*)$/m);
  topics.push({
    id: id,
    title: tm ? tm[1].trim() : id,
    html: renderMarkdown(body),
    text: body.replace(/[#*`|>-]/g, ' ').replace(/\s+/g, ' '),
  });
}

const now = new Date().toISOString().slice(0, 10);
const P2 = [];
const push = s => P2.push(s);

push('<!DOCTYPE html>');
push('<html lang="zh-CN"><head><meta charset="UTF-8">');
push('<meta name="viewport" content="width=device-width, initial-scale=1">');
push('<title>城市生命线数据上收平台 · 知识库</title>');
push('<style>');
push('*{box-sizing:border-box}');
push('body{margin:0;font-family:"Microsoft YaHei","PingFang SC",system-ui,sans-serif;color:#1f2329;background:#fff;line-height:1.75;font-size:15px}');
push('.layout{display:flex;min-height:100vh}');
push('aside{width:310px;flex:none;background:#0f172a;color:#cbd5e1;padding:20px 0;position:sticky;top:0;height:100vh;overflow-y:auto}');
push('aside .brand{padding:0 20px 14px;border-bottom:1px solid #1e293b;margin-bottom:12px}');
push('aside .brand b{color:#fff;font-size:15px;display:block;line-height:1.4}');
push('aside .brand span{font-size:12px;color:#64748b}');
push('aside input{width:calc(100% - 32px);margin:0 16px 12px;padding:7px 10px;border-radius:6px;border:1px solid #334155;background:#1e293b;color:#e2e8f0;font-size:13px;font-family:inherit}');
push('aside a{display:block;padding:8px 20px;color:#cbd5e1;text-decoration:none;font-size:13.5px;border-left:3px solid transparent}');
push('aside a:hover{background:#1e293b;color:#fff}');
push('aside a.on{background:#1e293b;color:#fff;border-left-color:#2563eb}');
push('aside a.hide{display:none}');
push('aside .meta{padding:14px 20px 0;font-size:11.5px;color:#475569;border-top:1px solid #1e293b;margin-top:12px;line-height:1.7}');
push('main{flex:1;min-width:0;padding:36px 48px 96px;max-width:1100px}');
push('.topic{display:none}.topic.on{display:block}');
push('h1{font-size:26px;margin:0 0 22px;padding-bottom:12px;border-bottom:2px solid #e3e6ea}');
push('h2{font-size:20px;margin:34px 0 14px;padding-left:11px;border-left:4px solid #2563eb}');
push('h3{font-size:16.5px;margin:26px 0 10px;color:#111827}');
push('h4{font-size:15px;margin:20px 0 8px;color:#374151}');
push('p{margin:10px 0}a{color:#2563eb}');
push('code{background:#f1f5f9;padding:1.5px 5px;border-radius:4px;font-family:Consolas,monospace;font-size:13px;color:#b91c1c}');
push('pre{background:#0f172a;color:#e2e8f0;padding:16px 18px;border-radius:8px;overflow-x:auto;font-size:13px;line-height:1.6}');
push('pre code{background:none;color:inherit;padding:0}');
push('.tw{overflow-x:auto;margin:14px 0}');
push('table{border-collapse:collapse;width:100%;font-size:13.5px}');
push('th,td{border:1px solid #e3e6ea;padding:8px 11px;text-align:left;vertical-align:top}');
push('th{background:#f8fafc;font-weight:600;white-space:nowrap}');
push('tbody tr:nth-child(even){background:#fcfcfd}');
push('blockquote{margin:14px 0;padding:11px 16px;background:#fffbeb;border-left:4px solid #f59e0b;color:#78350f;border-radius:0 6px 6px 0}');
push('blockquote p{margin:5px 0}');
push('blockquote code{background:rgba(0,0,0,.06);color:#78350f}');
push('hr{border:none;border-top:1px solid #e3e6ea;margin:30px 0}');
push('ul,ol{padding-left:26px;margin:10px 0}li{margin:5px 0}');
push('@media(max-width:860px){.layout{flex-direction:column}aside{width:100%;height:auto;position:relative}main{padding:20px}}');
push('</style></head><body><div class="layout"><aside>');
push('<div class="brand"><b>城市生命线数据上收平台</b><span>知识库 · 生成于 ' + now + '</span></div>');
push('<input id="q" placeholder="搜索主题…" autocomplete="off">');
push('<nav id="nav">');
topics.forEach((t, idx) => {
  push('<a href="#' + t.id + '" data-i="' + idx + '"' + (idx === 0 ? ' class="on"' : '') + '>' + esc(t.title) + '</a>');
});
push('</nav>');
push('<div class="meta">共 ' + topics.length + ' 个主题<br>上方搜索框可过滤主题</div>');
push('</aside><main>');
topics.forEach((t, idx) => {
  push('<section class="topic' + (idx === 0 ? ' on' : '') + '" id="' + t.id + '"><h1>' + esc(t.title) + '</h1>');
  push(t.html);
  push('</section>');
});
push('</main></div><script>');
push('var topics=' + JSON.stringify(topics.map(t => ({ id: t.id, text: t.text }))) + ';');
push('var nav=document.getElementById("nav"),q=document.getElementById("q");');
push('function show(id){');
push('  var s=document.querySelectorAll(".topic");');
push('  for(var i=0;i<s.length;i++)s[i].classList.toggle("on",s[i].id===id);');
push('  var a=nav.querySelectorAll("a");');
push('  for(var j=0;j<a.length;j++)a[j].classList.toggle("on",a[j].getAttribute("href")==="#"+id);');
push('  window.scrollTo(0,0);');
push('}');
push('nav.addEventListener("click",function(e){var a=e.target.closest("a");if(!a)return;e.preventDefault();show(a.getAttribute("href").slice(1));});');
push('q.addEventListener("input",function(){');
push('  var v=q.value.trim().toLowerCase(),a=nav.querySelectorAll("a");');
push('  for(var i=0;i<a.length;i++){var t=topics[Number(a[i].getAttribute("data-i"))];');
push('    var hit=!v||t.text.toLowerCase().indexOf(v)>=0||t.id.toLowerCase().indexOf(v)>=0;');
push('    a[i].classList.toggle("hide",!hit);}');
push('});');
push('document.addEventListener("click",function(e){');
push('  var a=e.target.closest("a");if(!a)return;var h=a.getAttribute("href")||"";');
push('  if(h.charAt(0)!=="#")return;var f=decodeURIComponent(h.slice(1));');
push('  var el=document.getElementById(f);if(!el)return;e.preventDefault();');
push('  var sec=el.closest(".topic");if(sec){show(sec.id);el.scrollIntoView();}');
push('});');
push('if(location.hash)show(decodeURIComponent(location.hash.slice(1)));');
push('</script></body></html>');

await fs.mkdir(path.dirname(outFile), { recursive: true });
await fs.writeFile(outFile, P2.join(NL), 'utf8');
const size = (await fs.stat(outFile)).size;
console.log('已生成 index.html（' + Math.round(size / 1024) + ' KB，' + topics.length + ' 个主题）');