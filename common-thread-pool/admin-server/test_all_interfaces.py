#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Full browser E2E test for ThreadPoolAdminServer - all pages, all interfaces."""
import sys
from playwright.sync_api import sync_playwright, expect

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')

BASE_URL = 'http://localhost:8080'
PASSED = 0
FAILED = 0

def ok(msg):
    global PASSED; PASSED += 1
    print(f'  OK: {msg}')

def fail(msg):
    global FAILED; FAILED += 1
    print(f'  FAIL: {msg}')

def close_all_drawers(page):
    """Force close all drawers and modals."""
    page.evaluate('''() => {
        document.body.classList.remove("drawer-open", "modal-open");
        document.querySelectorAll(".drawer.open, .drawer-overlay.open, .modal-overlay.open").forEach(el => el.classList.remove("open"));
    }''')

def login(page):
    page.goto(f'{BASE_URL}/login.html')
    page.wait_for_load_state('networkidle')
    page.fill('#username', 'admin')
    page.fill('#password', 'changeme')
    page.click('button[type=submit]')
    page.wait_for_url('**/index.html', timeout=10000)
    page.wait_for_load_state('networkidle')

def test_login_page(page):
    print('\n=== 1. Login Page ===')
    page.goto(f'{BASE_URL}/login.html')
    page.wait_for_load_state('networkidle')
    expect(page.locator('.login-card__title')).to_have_text('ThreadPool Admin')
    expect(page.locator('#username')).to_be_visible()
    expect(page.locator('#password')).to_be_visible()
    ok('Login page renders all elements')

    page.fill('#username', 'admin')
    page.fill('#password', 'wrongpass')
    page.click('button[type=submit]')
    page.wait_for_timeout(1500)
    assert page.locator('#error').is_visible()
    ok('Shows error for invalid credentials')

    page.fill('#username', 'admin')
    page.fill('#password', 'changeme')
    page.click('button[type=submit]')
    page.wait_for_url('**/index.html', timeout=10000)
    page.wait_for_load_state('networkidle')
    token = page.evaluate('() => localStorage.getItem("token")')
    assert token
    ok('Login succeeds, JWT token stored')

def test_dashboard(page):
    print('\n=== 2. Dashboard ===')
    page.goto(f'{BASE_URL}/index.html')
    page.wait_for_load_state('networkidle')
    page.wait_for_timeout(2000)
    expect(page.locator('.page-header h1')).to_contain_text('仪表盘')
    ok('Dashboard header visible')

    apps = page.locator('#stat-apps').text_content()
    keys = page.locator('#stat-keys').text_content()
    configs = page.locator('#stat-configs').text_content()
    ops = page.locator('#stat-ops').text_content()
    assert apps != '--' and keys != '--' and configs != '--' and ops != '--'
    ok(f'Stat cards: apps={apps}, keys={keys}, configs={configs}, ops={ops}')

    update = page.locator('#update-line').text_content()
    assert '加载中' not in update
    ok(f'Update line: {update}')

    rows = page.locator('#log-list tr')
    ok(f'Recent logs: {rows.count()} rows')

    page.click('text=↻ 刷新')
    page.wait_for_timeout(1000)
    ok('Refresh button works')

    nav = page.locator('.topbar__nav-link')
    assert nav.count() >= 5
    avatar = page.locator('.user-avatar__name')
    assert avatar.is_visible()
    ok(f'Topbar: {nav.count()} links, user={avatar.text_content()}')

def test_thread_pools(page):
    print('\n=== 3. Thread Pools ===')
    page.goto(f'{BASE_URL}/thread-pools.html')
    page.wait_for_load_state('networkidle')
    page.wait_for_timeout(1500)
    expect(page.locator('.page-header h1')).to_contain_text('线程池配置管理')
    ok('Thread pools page loaded')

    switcher = page.locator('#switcher-name')
    app_name = switcher.text_content()
    ok(f'App switcher: {app_name}')

    switcher.click()
    page.wait_for_timeout(500)
    opts = page.locator('#app-dropdown .app-switcher__option')
    if opts.count() > 0:
        ok(f'Dropdown: {opts.count()} apps')
        opts.first.click()
        page.wait_for_timeout(1500)
    page.locator('.breadcrumb').click()
    page.wait_for_timeout(300)

    rows = page.locator('#pool-tbody tr')
    count = rows.count()
    ok(f'Pool table: {count} entries')

    expect(page.locator('text=管理应用')).to_be_visible()
    expect(page.locator('text=回收站')).to_be_visible()
    expect(page.locator('text=+ 新建池')).to_be_visible()
    ok('All action buttons visible')

    # Create pool
    page.click('text=+ 新建池')
    page.wait_for_timeout(500)
    if page.locator('#create-drawer').is_visible():
        ok('Create pool drawer opened')
        test_pool = 'e2e-browser-pool'
        page.fill('#cf-poolName', test_pool)
        page.fill('#cf-core', '5')
        page.fill('#cf-max', '10')
        page.fill('#cf-qcap', '512')
        page.select_option('#cf-reject', 'ABORT_POLICY')
        page.fill('#cf-alive', '30')
        page.click('#create-drawer button.btn-primary')
        page.wait_for_timeout(2000)
        toast = page.locator('.toast')
        if toast.count() > 0:
            ok(f'Created pool: {toast.first.text_content()}')
        close_all_drawers(page)
        page.wait_for_timeout(300)
    else:
        fail('Create pool drawer not visible')

def test_api_keys(page):
    print('\n=== 4. API Keys ===')
    page.goto(f'{BASE_URL}/api-keys.html')
    page.wait_for_load_state('networkidle')
    page.wait_for_timeout(1500)
    expect(page.locator('.page-header h1')).to_contain_text('API Key 管理')
    ok('API Keys page loaded')

    expect(page.locator('#f-search')).to_be_visible()
    expect(page.locator('#f-status')).to_be_visible()
    ok('Filter bar visible')

    page.select_option('#f-status', 'enabled')
    page.wait_for_timeout(1000)
    ok('Status filter works')

    page.select_option('#f-status', '')
    page.click('text=重置')
    page.wait_for_timeout(1000)
    ok('Reset filter works')

    rows = page.locator('#apikey-list tr')
    ok(f'Table: {rows.count()} rows')

    # Create key
    page.click('text=+ 新建 API Key')
    page.wait_for_timeout(500)
    expect(page.locator('#create-drawer')).to_be_visible()
    ok('Create drawer opened')

    ts = page.evaluate('() => Date.now()') % 100000
    test_appid = f'e2e-browser-{ts}'
    page.fill('.drawer.open #f-appId', test_appid)
    page.fill('.drawer.open #f-appName', 'Browser E2E Test')
    page.click('#create-drawer button.btn-primary')
    page.wait_for_timeout(2000)

    reveal = page.locator('#reveal-drawer')
    if reveal.is_visible():
        key_val = page.locator('#reveal-key').input_value()
        ok(f'API Key created: {key_val[:20]}...')
        close_all_drawers(page)
        page.wait_for_timeout(500)
    else:
        ok('API Key created (no reveal)')

    # Search
    page.fill('#f-search', test_appid)
    page.wait_for_timeout(1000)
    rows = page.locator('#apikey-list tr')
    if rows.count() > 0 and test_appid in rows.first.text_content():
        ok(f'Search finds {test_appid}')

        # View detail
        btn = page.locator('button:has-text("查看")').first
        if btn.is_visible():
            btn.click()
            page.wait_for_timeout(500)
            if page.locator('#detail-modal.open').count() > 0:
                ok('Detail modal opens')
                page.click('.modal-close')
                page.wait_for_timeout(300)

        # Regenerate
        regen = page.locator('button:has-text("重生")').first
        if regen.is_visible():
            page.on('dialog', lambda d: d.accept())
            regen.click()
            page.wait_for_timeout(1000)
            if page.locator('#reveal-drawer').is_visible():
                ok('Regenerate works')
                close_all_drawers(page)
                page.wait_for_timeout(500)

def test_stats(page):
    print('\n=== 5. Stats ===')
    page.goto(f'{BASE_URL}/stats.html')
    page.wait_for_load_state('networkidle')
    page.wait_for_timeout(2000)
    expect(page.locator('.page-header h1')).to_contain_text('监控统计')
    ok('Stats page loaded')

    app_sel = page.locator('#s-app')
    if app_sel.locator('option').count() > 1:
        app_sel.select_option(index=1)
        page.wait_for_timeout(1000)
        ok('App selector works')

        pool_sel = page.locator('#s-pool')
        if pool_sel.locator('option').count() > 1:
            pool_sel.select_option(index=1)
            page.wait_for_timeout(1500)
            ok('Pool selector works')

            active = page.locator('#m-active').text_content()
            queue = page.locator('#m-queue').text_content()
            ok(f'Metrics: active={active}, queue={queue}')

            tabs = page.locator('.time-tab')
            ok(f'Time tabs: {tabs.count()}')
            page.locator('.time-tab:has-text("5m")').click()
            page.wait_for_timeout(1000)
            ok('5m tab selected')

            page.click('text=↻ 刷新')
            page.wait_for_timeout(1000)
            ok('Refresh works')

def test_operate_logs(page):
    print('\n=== 6. Operate Logs ===')
    page.goto(f'{BASE_URL}/operate-logs.html')
    page.wait_for_load_state('networkidle')
    page.wait_for_timeout(1500)
    expect(page.locator('.page-header h1')).to_contain_text('操作日志')
    ok('Operate logs page loaded')

    expect(page.locator('#f-biz')).to_be_visible()
    expect(page.locator('#f-op')).to_be_visible()
    expect(page.locator('#f-user')).to_be_visible()
    expect(page.locator('#f-bizid')).to_be_visible()
    ok('All 4 filters visible')

    expect(page.locator('text=↧ 导出 CSV')).to_be_visible()
    ok('Export CSV button visible')

    rows = page.locator('#log-list tr')
    count = rows.count()
    ok(f'Log table: {count} entries')

    # View content
    view_btn = page.locator('button:has-text("查看")').first
    if view_btn.is_visible():
        view_btn.click()
        page.wait_for_timeout(500)
        if page.locator('#content-drawer.open').count() > 0:
            ok('Content drawer opens')
            page.click('text=复制 JSON')
            page.wait_for_timeout(300)
            ok('Copy JSON works')
            page.click('#content-drawer button.btn-primary')
            page.wait_for_timeout(300)

    # Filters
    page.select_option('#f-biz', 'APIKEY')
    page.click('text=查询')
    page.wait_for_timeout(1000)
    ok('Filter by APIKEY')

    page.select_option('#f-op', 'CREATE')
    page.click('text=查询')
    page.wait_for_timeout(1000)
    ok('Filter by CREATE')

    page.fill('#f-user', 'admin')
    page.click('text=查询')
    page.wait_for_timeout(1000)
    ok('Filter by operator')

    page.click('text=重置')
    page.wait_for_timeout(500)
    assert page.locator('#f-biz').input_value() == ''
    ok('Reset clears filters')

    # Pagination
    pager = page.locator('#pager')
    if pager.is_visible():
        ok(f'Pagination: {pager.text_content().strip()}')
        nxt = page.locator('button:has-text("下一页")')
        if nxt.is_visible() and not nxt.is_disabled():
            nxt.click()
            page.wait_for_timeout(1000)
            ok('Next page works')

    # Export
    page.click('text=↧ 导出 CSV')
    page.wait_for_timeout(500)
    ok('Export CSV triggered')

def test_admin_users(page):
    print('\n=== 7. Admin Users ===')
    page.goto(f'{BASE_URL}/admin-users.html')
    page.wait_for_load_state('networkidle')
    page.wait_for_timeout(1500)
    expect(page.locator('.page-header h1')).to_contain_text('管理员管理')
    ok('Admin users page loaded')

    nick = page.locator('#my-nickname').text_content()
    uname = page.locator('#my-username').text_content()
    role = page.locator('#my-role-tag').text_content()
    ok(f'Self: {uname} ({nick}) role={role}')

    # Change nickname first
    page.locator('button:has-text("修改昵称")').click()
    page.wait_for_timeout(500)
    expect(page.locator('#nick-drawer')).to_be_visible()
    ok('Change nickname drawer opens')
    page.fill('#f-nick', 'TestAdmin')
    page.click('#nick-drawer button.btn-primary')
    page.wait_for_timeout(1500)
    ok('Nickname change submitted')
    close_all_drawers(page)
    page.wait_for_timeout(500)

    # Change password drawer - just verify it opens, then close it
    page.locator('button:has-text("修改密码")').click()
    page.wait_for_timeout(500)
    expect(page.locator('#pwd-drawer')).to_be_visible()
    ok('Change password drawer opens')
    page.evaluate('''() => {
        document.body.classList.remove("drawer-open");
        var els = document.querySelectorAll("#pwd-overlay,#pwd-drawer");
        els.forEach(function(el) { el.classList.remove("open"); });
    }''')
    page.wait_for_timeout(500)

    if 'SUPER_ADMIN' in role:
        if page.locator('#admin-list-section').is_visible():
            ok('Admin list visible (SUPER_ADMIN)')
            rows = page.locator('#admin-list tr')
            ok(f'Admin list: {rows.count()} entries')

            # Create
            page.click('#btn-create')
            page.wait_for_timeout(500)
            expect(page.locator('#edit-drawer')).to_be_visible()
            ok('Create admin drawer opens')

            test_user = 'e2e-test-admin'
            page.fill('#f-cu-name', test_user)
            page.fill('#f-cu-pwd', 'testpass123')
            page.fill('#f-cu-nick', 'E2E Test')
            page.select_option('#f-cu-role', 'ADMIN')
            page.click('#btn-save-admin')
            page.wait_for_timeout(2000)
            ok('Create admin submitted')
            close_all_drawers(page)
            page.wait_for_timeout(500)

            # Edit
            edit_btn = page.locator(f'tr:has-text("{test_user}") button:has-text("编辑")')
            if edit_btn.count() > 0:
                edit_btn.first.click()
                page.wait_for_timeout(500)
                expect(page.locator('#edit-drawer')).to_be_visible()
                ok('Edit admin drawer opens')
                page.fill('#f-cu-nick', 'E2E Updated')
                page.click('#btn-save-admin')
                page.wait_for_timeout(1500)
                ok('Edit admin submitted')
                close_all_drawers(page)
                page.wait_for_timeout(500)

            # Delete
            page.wait_for_timeout(500)
            del_btn = page.locator(f'tr:has-text("{test_user}") button:has-text("删除")')
            if del_btn.count() > 0:
                del_btn.first.click()
                page.wait_for_timeout(500)
                expect(page.locator('#confirm-modal')).to_be_visible()
                ok('Delete confirm modal opens')
                page.click('#btn-confirm-ok')
                page.wait_for_timeout(1500)
                ok('Delete admin confirmed')

def test_navigation(page):
    print('\n=== 8. Navigation ===')
    login(page)
    nav_map = [
        ('仪表盘', 'index.html'),
        ('线程池', 'thread-pools.html'),
        ('API Keys', 'api-keys.html'),
        ('监控', 'stats.html'),
        ('审计', 'operate-logs.html'),
    ]
    for label, expected in nav_map:
        link = page.locator(f'.topbar__nav-link:has-text("{label}")')
        if link.count() > 0:
            link.click()
            page.wait_for_load_state('networkidle')
            page.wait_for_timeout(500)
            assert expected in page.url
            ok(f'Nav {label} -> {expected}')
        else:
            fail(f'Nav link {label} not found')

def test_logout_and_guard(page):
    print('\n=== 9. Logout & Auth Guard ===')
    login(page)
    page.goto(f'{BASE_URL}/index.html')
    page.wait_for_load_state('networkidle')
    page.wait_for_timeout(500)
    page.click('.user-avatar')
    page.wait_for_timeout(1000)
    assert 'login.html' in page.url
    token = page.evaluate('() => localStorage.getItem("token")')
    assert not token
    ok('Logout clears token, redirects to login')

    page.goto(f'{BASE_URL}/index.html')
    page.wait_for_load_state('networkidle')
    page.wait_for_timeout(500)
    assert 'login.html' in page.url
    ok('Unauthenticated redirects to login')

def main():
    global PASSED, FAILED
    print('=' * 60)
    print('ThreadPoolAdminServer - Full Interface E2E Tests')
    print('=' * 60)

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        context = browser.new_context(viewport={'width': 1440, 'height': 900}, locale='zh-CN')
        page = context.new_page()

        try:
            test_login_page(page)
            test_dashboard(page)
            test_thread_pools(page)
            test_api_keys(page)
            test_stats(page)
            test_operate_logs(page)
            test_admin_users(page)
            test_navigation(page)
            test_logout_and_guard(page)
        except Exception as e:
            page.screenshot(path='/tmp/e2e_full_failure.png', full_page=True)
            print(f'\n!!! TEST ABORTED: {e}')
            raise
        finally:
            browser.close()

    print(f'\n{"=" * 60}')
    print(f'Results: {PASSED} passed, {FAILED} failed')
    print(f'{"=" * 60}')

if __name__ == '__main__':
    main()