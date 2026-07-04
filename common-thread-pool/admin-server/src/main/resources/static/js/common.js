// ===== Token =====
function getToken() { return localStorage.getItem('token'); }
function setToken(t) { localStorage.setItem('token', t); }
function clearToken() { localStorage.removeItem('token'); localStorage.removeItem('username'); }
function getUsername() {
  const u = localStorage.getItem('username');
  if (u) return u;
  const t = getToken();
  if (!t) return 'admin';
  try { return JSON.parse(atob(t.split('.')[1])).username || 'admin'; }
  catch { return 'admin'; }
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

// ===== Topbar =====
function renderTopbar(active) {
  const u = getUsername();
  const links = [
    ['index.html', '仪表盘'],
    ['api-keys.html', 'API Key'],
    ['thread-pools.html', '线程池配置'],
    ['operate-logs.html', '操作日志'],
  ];
  const nav = links.map(([href, label]) =>
    `<a href="/${href}" class="${href === active ? 'active' : ''}">${label}</a>`
  ).join('');
  document.body.insertAdjacentHTML('afterbegin', `
    <header class="topbar">
      <div class="logo">ThreadPool Admin</div>
      <nav>${nav}</nav>
      <div class="user-info">Hi, <span>${u}</span></div>
      <button class="btn-logout" onclick="logout()">退出</button>
    </header>
  `);
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
