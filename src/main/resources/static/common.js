// 公共工具：所有页面共用
const api = {
  async get(url) {
    const r = await fetch(url);
    const text = await r.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch (e) { data = { raw: text }; }
    if (!r.ok) throw Object.assign(new Error('HTTP ' + r.status), { status: r.status, data });
    return data;
  },
  async send(method, url, body) {
    const r = await fetch(url, {
      method,
      headers: body ? { 'Content-Type': 'application/json' } : {},
      body: body ? JSON.stringify(body) : undefined,
    });
    const text = await r.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch (e) { data = { raw: text }; }
    if (!r.ok) throw Object.assign(new Error('HTTP ' + r.status), { status: r.status, data });
    return data;
  },
};

function esc(v) {
  if (v === null || v === undefined) return '';
  return String(v).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function fmtTime(v) {
  if (!v) return '-';
  return String(v).replace('T', ' ').slice(0, 19);
}

function statusBadge(status) {
  const map = {
    ACCEPTED_OLD: 'ok', REJECTED_OLD: 'danger', REJECTED_LOCAL: 'danger',
    UNKNOWN: 'warn', RECEIVED: 'info',
  };
  return '<span class="badge ' + (map[status] || '') + '">' + esc(status) + '</span>';
}

function toast(el, message, kind) {
  if (!el) return;
  el.className = 'notice' + (kind ? ' ' + kind : '');
  el.textContent = message;
  el.style.display = message ? 'block' : 'none';
}

function renderNav(active) {
  const links = [
    ['index.html', '概览'],
    ['credentials.html', '凭证登记'],
    ['debug.html', '接口调试台'],
    ['records.html', '上收数据'],
    ['kb/index.html', '知识库'],
  ];
  document.write('<header><h1>城市生命线数据上收开放平台</h1><nav>' +
    links.map(([href, label]) =>
      '<a href="' + href + '"' + (href === active ? ' class="active"' : '') + '>' + label + '</a>'
    ).join('') + '</nav></header>');
}
