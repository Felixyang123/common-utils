// ===== Token =====
function getToken() { return localStorage.getItem('token'); }
function setToken(t) { localStorage.setItem('token', t); }
function clearToken() { localStorage.removeItem('token'); localStorage.removeItem('username'); }

/**
 * Parse JWT payload (Base64URL + UTF-8 safe).
 * Native atob() produces a binary string that JSON.parse cannot handle
 * when the payload contains multi-byte UTF-8 characters (e.g. Chinese nicknames).
 */
function parseJwtPayload(token) {
  var payload = token.split('.')[1];
  var base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
  while (base64.length % 4) base64 += '=';
  var binary = atob(base64);
  var bytes = new Uint8Array(binary.length);
  for (var i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return new TextDecoder().decode(bytes);
}

function getUsername() {
  const u = localStorage.getItem('username');
  if (u) return u;
  const t = getToken();
  if (!t) return 'admin';
  try { return JSON.parse(parseJwtPayload(t)).username || 'admin'; }
  catch { return 'admin'; }
}

function getRole() {
  const t = getToken();
  if (!t) return 'ADMIN';
  try { return JSON.parse(parseJwtPayload(t)).role || 'ADMIN'; }
  catch { return 'ADMIN'; }
}

function getNickname() {
  const t = getToken();
  if (!t) return '';
  try { return JSON.parse(parseJwtPayload(t)).nickname || getUsername(); }
  catch { return getUsername(); }
}

function isSuperAdmin() {
  return getRole() === 'SUPER_ADMIN';
}

// ===== Auth guard =====
function requireAuth() {
  if (!getToken()) { location.href = '/login.html'; return false; }
  return true;
}

// ===== Fetch wrapper =====
async function authedFetch(url, opts = {}) {
  opts.headers = opts.headers || {};
  const t = getToken();
  if (t) opts.headers['Authorization'] = 'Bearer ' + t;
  if (opts.body && typeof opts.body === 'object' && !(opts.body instanceof FormData)) {
    opts.headers['Content-Type'] = 'application/json';
    opts.body = JSON.stringify(opts.body);
  }
  const res = await fetch(url, opts);
  const nt = res.headers.get('X-New-Token');
  if (nt) setToken(nt);
  if (res.status === 401) { clearToken(); location.href = '/login.html'; return null; }
  return res;
}

async function apiGet(url) {
  const res = await authedFetch(url);
  if (!res) return null;
  return res.json();
}

async function apiPost(url, body) {
  const res = await authedFetch(url, { method: 'POST', body });
  if (!res) return null;
  return res.json();
}

async function apiDelete(url) {
  const res = await authedFetch(url, { method: 'DELETE' });
  if (!res) return null;
  return res.json();
}

async function apiPut(url, body) {
  const res = await authedFetch(url, { method: 'PUT', body });
  if (!res) return null;
  return res.json();
}

// ===== Logout =====
async function logout() {
  try { await authedFetch('/api/auth/logout', { method: 'POST' }); } catch {}
  clearToken();
  location.href = '/login.html';
}

// ===== Topbar (v2) =====
function renderTopbar(active) {
  const u = getUsername();
  const nick = getNickname();
  const displayName = nick || u;
  const initial = (displayName || 'A').charAt(0).toUpperCase();
  const links = [
    ['index.html',           '仪表盘',     '📊'],
    ['thread-pools.html',    '线程池',     '⚙'],
    ['api-keys.html',        'API Keys',  '🔑'],
    ['stats.html',           '监控',       '📈'],
    ['operate-logs.html',    '审计',       '📋'],
  ];
  if (isSuperAdmin()) links.push(['admin-users.html', '管理员', '👥']);
  const nav = links.map(([href, label, icon]) =>
    `<a href="/${href}" class="topbar__nav-link ${href === active ? 'active' : ''}">${label}</a>`
  ).join('');
  document.getElementById('topbar').innerHTML = `
    <a href="/index.html" class="topbar__brand">
      <div class="topbar__logo">T</div>
      <span class="topbar__brand-text">ThreadPool Admin</span>
    </a>
    <nav class="topbar__nav">${nav}</nav>
    <div class="topbar__right">
      <div class="sync-indicator" title="配置中心已连接">
        <span class="sync-indicator__dot"></span>
        实时同步
      </div>
      <div class="user-avatar" onclick="logout()" title="点击退出登录">
        <div class="user-avatar__img">${initial}</div>
        <span class="user-avatar__name">${displayName}</span>
      </div>
    </div>
  `;
}

// ===== Modal =====
function openModal(id) { document.getElementById(id).classList.add('open'); }
function closeModal(id) { document.getElementById(id).classList.remove('open'); }

// ===== Badge helpers =====
function badgeEnabled(v) { return v ? '<span class="badge badge-green">启用</span>' : '<span class="badge badge-red">禁用</span>'; }
function badgeExpired(v) { return v ? '<span class="badge badge-red">已过期</span>' : '<span class="badge badge-green">有效</span>'; }
function badgeOpType(t) {
  const m = { CREATE:'badge-green', UPDATE:'badge-blue', DELETE:'badge-red', REGENERATE:'badge-yellow', ROLLBACK:'badge-yellow', UPSERT:'badge-blue' };
  return `<span class="badge ${m[t]||'badge-gray'}">${t}</span>`;
}
function badgeBizType(t) {
  const m = { APIKEY:'badge-yellow', THREADPOOL_CONFIG:'badge-blue' };
  return `<span class="badge ${m[t]||'badge-gray'}">${t}</span>`;
}

// ===== Time =====
function fmtTime(t) {
  if (!t) return '-';
  return t.replace('T', ' ').substring(0, 19);
}
