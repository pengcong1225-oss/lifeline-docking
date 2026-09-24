// 公共工具：所有页面共用

// 应用上下文根路径。
//
// 页面可能被部署在域名根，也可能被反向代理挂在子路径下（如 http://host/lifeline/）。
// 此时写死的 '/admin/api/...' 会跳到域名根而不是应用根，导致 404 或拿到 HTML 错误页。
// 这里根据当前页面地址推导出应用根，让所有接口调用都用相对路径。
let APP_BASE = (function () {
  let p = window.location.pathname;
  // 去掉页面文件名，例如 /lifeline/debug.html -> /lifeline/
  if (p.endsWith('/')) return p;
  const slash = p.lastIndexOf('/');
  return slash >= 0 ? p.slice(0, slash + 1) : '/';
})();

// 把以 / 开头的接口路径拼到应用根后面
function apiUrl(path) {
  if (!path.startsWith('/')) return APP_BASE + path;
  return APP_BASE + path.slice(1);
}

const api = {
  async get(url) {
    const r = await fetch(apiUrl(url));
    const text = await r.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch (e) { data = { raw: text }; }
    if (!r.ok) throw Object.assign(new Error('HTTP ' + r.status), { status: r.status, data });
    return data;
  },
  async send(method, url, body) {
    const r = await fetch(apiUrl(url), {
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

// 导航使用相对当前页面的路径，避免子路径部署时跳错
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