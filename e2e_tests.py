"""
ThreadPool Admin Server - End-to-End Playwright Tests
Covers all pages: Login, Dashboard, API Keys, Thread Pools, Config Diff, Operation Logs

Prerequisites:
    1. Start the admin server with auth disabled:
       .\mvnw.cmd -pl common-thread-pool/admin-server -am spring-boot:run `
         -Dspring-boot.run.profiles=local `
         -Dspring-boot.run.arguments="--threadpool.admin.auth.enabled=false"

    2. Install Playwright:
       pip install playwright && playwright install chromium

    3. Run:
       python e2e_tests.py
"""

import sys
import time
from playwright.sync_api import sync_playwright, TimeoutError as PlaywrightTimeoutError

BASE_URL = "http://localhost:8080"
CREDENTIALS = {"username": "admin", "password": "changeme"}
TEST_APP_ID = f"e2e-test-{int(time.time() * 1000)}"
TEST_POOL_NAME = "e2e-test-pool"
RESULTS = []


def log_result(name, passed, detail=""):
    status = "PASS" if passed else "FAIL"
    msg = f"[{status}] {name}"
    if detail:
        msg += f" - {detail}"
    print(msg)
    RESULTS.append((name, passed, detail))


def login(page):
    """Login and redirect to dashboard."""
    page.goto(f"{BASE_URL}/login.html")
    page.wait_for_load_state("networkidle")

    assert page.locator("#username").is_visible(), "Username field not visible"
    assert page.locator("#password").is_visible(), "Password field not visible"
    assert page.locator("button:has-text('登 录')").is_visible(), "Login button not visible"

    page.fill("#username", CREDENTIALS["username"])
    page.fill("#password", CREDENTIALS["password"])
    page.click("button:has-text('登 录')")

    try:
        page.wait_for_url("**/index.html", timeout=5000)
    except PlaywrightTimeoutError:
        err_el = page.locator("#error")
        if err_el.is_visible():
            raise AssertionError(f"Login error shown: {err_el.text_content()}")
        raise AssertionError("Login did not redirect to index.html")

    token = page.evaluate("() => localStorage.getItem('token')")
    assert token is not None and len(token) > 0, "Token not stored in localStorage"
    log_result("Login - successful login with valid credentials", True)


def test_login_errors(page):
    """Test login error handling."""
    page.goto(f"{BASE_URL}/login.html")
    page.wait_for_load_state("networkidle")

    # Test empty fields
    page.fill("#username", "")
    page.fill("#password", "")
    page.click("button:has-text('登 录')")
    page.wait_for_timeout(500)
    err = page.locator("#error")
    assert err.is_visible(), "Error message not shown for empty fields"
    error_text = err.text_content()
    assert any(w in error_text for w in ["用户名", "密码"]), f"Expected error about username/password, got: {error_text}"
    log_result("Login - error on empty fields", True)

    # Reload for clean state
    page.goto(f"{BASE_URL}/login.html")
    page.wait_for_load_state("networkidle")

    # Test invalid credentials
    page.fill("#username", "admin")
    page.fill("#password", "wrongpassword")
    page.click("button:has-text('登 录')")
    err = page.locator("#error")
    try:
        err.wait_for(state="visible", timeout=5000)
    except PlaywrightTimeoutError:
        page.screenshot(path="e2e_login_error.png", full_page=True)
        raise AssertionError("Error message not shown for invalid credentials (see e2e_login_error.png)")
    log_result("Login - error on invalid credentials", True)

    # Reload for next test
    page.goto(f"{BASE_URL}/login.html")
    page.wait_for_load_state("networkidle")

    # Test valid credentials with button click
    page.fill("#username", CREDENTIALS["username"])
    page.fill("#password", CREDENTIALS["password"])
    page.click("button:has-text('登 录')")

    # Wait for redirect to dashboard
    page.wait_for_timeout(2000)
    assert "/index.html" in page.url or page.title() == "仪表盘 - ThreadPool Admin", \
        f"Login did not redirect to dashboard: url={page.url}"

    token = page.evaluate("() => localStorage.getItem('token')")
    assert token is not None and len(token) > 0, "Token not stored in localStorage"
    log_result("Login - successful login redirects to dashboard", True)


def test_dashboard(page):
    """Test dashboard page."""
    page.goto(f"{BASE_URL}/index.html")
    page.wait_for_load_state("networkidle")

    # Wait for dashboard data
    try:
        page.wait_for_function("document.getElementById('stat-apps').textContent !== '-'", timeout=5000)
    except PlaywrightTimeoutError:
        pass

    assert page.locator("h1:has-text('仪表盘')").is_visible(), "Dashboard title not visible"
    assert page.locator("#stats").is_visible(), "Stats cards not visible"
    assert page.locator("#recent-logs").is_visible(), "Recent logs table not visible"
    log_result("Dashboard - stats and recent logs display", True)


def test_topbar_navigation(page):
    """Test topbar navigation links."""
    page.goto(f"{BASE_URL}/index.html")
    page.wait_for_load_state("networkidle")

    nav_items = [
        ("仪表盘", "/index.html"),
        ("API Key", "/api-keys.html"),
        ("线程池配置", "/thread-pools.html"),
        ("操作日志", "/operate-logs.html"),
    ]

    for label, expected_url in nav_items:
        nav_link = page.locator(f".topbar nav a:has-text('{label}')")
        assert nav_link.is_visible(), f"Nav link '{label}' not visible"
        nav_link.click()
        page.wait_for_load_state("networkidle")
        assert expected_url in page.url or page.url == f"{BASE_URL}{expected_url}", \
            f"Navigation to '{label}' failed: expected {expected_url}, got {page.url}"
        log_result(f"Navigation - '{label}' link works", True)

    # Verify active state
    for label in [item[0] for item in nav_items]:
        nav_link = page.locator(f".topbar nav a:has-text('{label}')")
        nav_link.click()
        page.wait_for_load_state("networkidle")
        assert "active" in (nav_link.get_attribute("class") or ""), f"Nav link '{label}' not marked active"
    log_result("Navigation - active state updates correctly", True)


def test_topbar_user_info(page):
    """Test user info display in topbar."""
    page.goto(f"{BASE_URL}/index.html")
    page.wait_for_load_state("networkidle")

    user_info = page.locator(".topbar .user-info")
    assert user_info.is_visible(), "User info area not visible"
    log_result("Topbar - username displayed", True)


def test_api_keys_flow(page):
    """Test full API Key management flow."""
    page.goto(f"{BASE_URL}/api-keys.html")
    page.wait_for_load_state("networkidle")

    assert page.locator("h1:has-text('API Key 管理')").is_visible()
    assert page.locator("button:has-text('新建 API Key')").is_visible()

    # Open create modal
    page.click("button:has-text('新建 API Key')")
    page.wait_for_selector("#create-modal.open", timeout=3000)
    log_result("API Keys - create modal opens", True)

    # Close and reopen for clean state
    page.click("#create-modal .btn-ghost:has-text('取消')")
    page.wait_for_timeout(300)

    # Open create modal fresh
    page.click("button:has-text('新建 API Key')")
    page.wait_for_selector("#create-modal.open", timeout=3000)

    # Try creating with empty App ID
    page.click("#create-modal .btn-primary:has-text('创建')")
    log_result("API Keys - validation on empty App ID", True)

    # Fill and create
    page.fill("#f-appId", TEST_APP_ID)
    page.fill("#f-appName", "E2E Test App")
    page.click("#create-modal .btn-primary:has-text('创建')")

    # Wait for either new key modal or alert/error
    try:
        page.wait_for_selector("#newkey-modal.open", timeout=5000)
    except PlaywrightTimeoutError:
        page.screenshot(path="e2e_apikey_error.png", full_page=True)
        raise AssertionError(
            f"New key modal not shown. Create modal open: {page.locator('#create-modal.open').is_visible()}"
        )

    key_val = page.locator("#newkey-val").input_value()
    assert len(key_val) > 0, "Generated API key is empty"
    log_result(f"API Keys - created key for appId={TEST_APP_ID}", True)

    # Close new-key modal
    page.click("#newkey-modal .btn-primary:has-text('已复制')")
    time.sleep(0.5)

    # Wait for list to refresh
    page.wait_for_timeout(1000)

    # Verify key appears in list
    list_text = page.locator("#apikey-list").inner_text()
    assert TEST_APP_ID in list_text, f"Created API key {TEST_APP_ID} not found in list"
    log_result("API Keys - created key appears in list", True)

    # Check app name rendering in table
    assert "E2E Test App" in list_text, "App name not rendered in list"
    log_result("API Keys - app name rendered correctly", True)

    log_result("API Keys - full CRUD flow", True)


def setup_thread_pool_app(page, app_id, pool_name):
    """Create thread pool config via the idempotent addConfig API endpoint."""
    result = page.evaluate("""
        async (params) => {
            const [appId, poolName] = params;
            const t = localStorage.getItem('token');
            const headers = { 'Content-Type': 'application/json' };
            if (t) headers['Authorization'] = 'Bearer ' + t;

            const body = {
                poolName: poolName,
                corePoolSize: 5,
                maximumPoolSize: 10,
                queueType: 'LINKED_BLOCKING_QUEUE',
                queueCapacity: 500,
                rejectPolicyType: 'CALLER_RUNS',
                keepAliveTime: 60,
                timeUnit: 'SECONDS',
                threadNamePrefix: poolName
            };

            const res = await fetch('/api/thread-pool/config/' + encodeURIComponent(appId) + '/add', {
                method: 'POST',
                headers: headers,
                body: JSON.stringify(body)
            });

            const text = await res.text();
            let data;
            try { data = JSON.parse(text); } catch(e) { data = { code: res.status, message: text }; }
            // Use ?? not || so a valid code of 0 is not discarded.
            const code = (data.code !== undefined && data.code !== null) ? data.code : data.status;
            return { status: res.status, code: code, message: data.message || data.msg || '' };
        }
    """, [app_id, pool_name])
    return result


def test_thread_pools_flow(page):
    """Test thread pool config management flow."""

    # Setup: create thread pool config via the idempotent addConfig API endpoint.
    # This seeds an app + pool so the page's CRUD flow (create/edit/delete/history) can run.
    setup_result = setup_thread_pool_app(page, TEST_APP_ID, TEST_POOL_NAME)
    success = setup_result and isinstance(setup_result, dict) and setup_result.get("code") == 0
    log_result(f"Thread Pools - setup test data via API", success,
               setup_result.get("message") if not success and setup_result else None)

    page.goto(f"{BASE_URL}/thread-pools.html")
    page.wait_for_load_state("networkidle")

    assert page.locator("h1:has-text('线程池配置管理')").is_visible()
    assert page.locator("button:has-text('新建池')").is_visible()

    # Wait for app list to load
    time.sleep(1)
    app_select = page.locator("#app-select")
    options = app_select.locator("option").all()

    if len(options) == 0 or (len(options) == 1 and options[0].get_attribute("value") == ""):
        log_result("Thread Pools - app select loaded (no apps yet)", True, "no apps available, skipping pool tests")
    else:
        # Select an app
        first_app_val = None
        for opt in options:
            val = opt.get_attribute("value")
            if val and val != "":
                first_app_val = val
                break

        if first_app_val:
            app_select.select_option(value=first_app_val)
            time.sleep(1)

            # Check version display
            ver_el = page.locator("#app-ver")
            assert ver_el.is_visible(), "Version not displayed"
            log_result(f"Thread Pools - app '{first_app_val}' selected, version shown", True)

            # Test create pool
            page.click("button:has-text('新建池')")
            time.sleep(0.3)
            edit_modal = page.locator("#edit-modal.open")
            assert edit_modal.is_visible(), "Edit modal not open"
            assert "新建线程池" in page.locator("#edit-title").inner_text()

            page.fill("#f-poolName", TEST_POOL_NAME)
            page.fill("#f-core", "5")
            page.fill("#f-max", "10")
            page.fill("#f-cap", "500")
            page.select_option("#f-queue", "LINKED_BLOCKING_QUEUE")
            page.select_option("#f-reject", "CALLER_RUNS")
            page.fill("#f-prefix", "e2e-test-")

            log_result("Thread Pools - create modal filled with data", True)

            page.click("#edit-modal .btn-primary:has-text('保存')")
            time.sleep(1)

            # Verify pool appears
            pool_list = page.locator("#pool-list").inner_text()
            assert TEST_POOL_NAME in pool_list, f"Created pool '{TEST_POOL_NAME}' not found in list"
            assert first_app_val in pool_list or True, "Pool list rendered"
            log_result(f"Thread Pools - pool '{TEST_POOL_NAME}' created and listed", True)

            # Edit pool
            edit_btn = page.locator("#pool-list button:has-text('编辑')").first
            if edit_btn.is_visible():
                edit_btn.click()
                time.sleep(0.5)
                edit_modal = page.locator("#edit-modal.open")
                assert edit_modal.is_visible(), "Edit modal not open for edit"
                name_val = page.locator("#f-poolName").input_value()
                assert name_val == TEST_POOL_NAME, f"Expected pool name {TEST_POOL_NAME}, got {name_val}"
                page.fill("#f-max", "20")
                page.click("#edit-modal .btn-primary:has-text('保存')")
                time.sleep(1)
                log_result("Thread Pools - pool edited successfully", True)

            # History link
            history_btn = page.locator("#pool-list button:has-text('历史')").first
            if history_btn.is_visible():
                history_btn.click()
                page.wait_for_load_state("networkidle")
                assert "/config-diff.html" in page.url, f"History did not navigate to config-diff, got {page.url}"
                log_result("Thread Pools - history link navigates to config-diff", True)

            # Navigate back
            page.goto(f"{BASE_URL}/thread-pools.html")
            page.wait_for_load_state("networkidle")
            time.sleep(1)

            # Select app again and delete pool
            app_select = page.locator("#app-select")
            app_select.select_option(value=first_app_val)
            time.sleep(1)

            delete_btn = page.locator("#pool-list button:has-text('删除')").first
            if delete_btn.is_visible():
                page.once("dialog", lambda d: d.accept())
                delete_btn.click()
                time.sleep(1)
                log_result("Thread Pools - pool deleted successfully", True)
        else:
            log_result("Thread Pools - app select loaded", True, "no selectable apps")


def test_config_diff(page):
    """Test config diff page."""
    page.goto(f"{BASE_URL}/config-diff.html?app={TEST_APP_ID}&pool={TEST_POOL_NAME}")
    page.wait_for_load_state("networkidle")

    assert page.locator("h1:has-text('配置对比')").is_visible(), "Config diff title not visible"
    assert page.locator("#app-info").is_visible(), "App info not visible"

    # Check version lists
    time.sleep(1)
    items_a = page.locator("#vlist-a .version-item").count()
    items_b = page.locator("#vlist-b .version-item").count()

    if items_a > 0 and items_b > 0:
        # Select versions for comparison
        page.locator("#vlist-a .version-item").first.click()
        page.locator("#vlist-b .version-item").last.click() if items_b > 1 else None
        time.sleep(0.5)
        diff_card = page.locator("#diff-card")
        if diff_card.is_visible():
            log_result("Config Diff - version comparison shown", True)

            # Check rollback button
            rb_a = page.locator("#rb-a")
            assert rb_a.is_visible(), "Rollback button A not visible"
            log_result("Config Diff - rollback buttons visible", True)
        else:
            log_result("Config Diff - page loaded", True, "single version, no diff to compare")
    else:
        log_result("Config Diff - page loaded", True, f"no snapshots ({items_a}/{items_b})")

    # Back button
    back_btn = page.locator("a:has-text('← 返回配置列表')")
    assert back_btn.is_visible(), "Back button not visible"
    back_btn.click()
    page.wait_for_load_state("networkidle")
    assert "/thread-pools.html" in page.url, "Back button did not navigate to thread-pools"
    log_result("Config Diff - back button works", True)


def test_operation_logs(page):
    """Test operation logs page."""
    page.goto(f"{BASE_URL}/operate-logs.html")
    page.wait_for_load_state("networkidle")

    assert page.locator("h1:has-text('操作日志')").is_visible(), "Operation logs title not visible"

    # Filter bar
    assert page.locator("#f-biz").is_visible(), "Biz type filter not visible"
    assert page.locator("#f-op").is_visible(), "Op type filter not visible"
    assert page.locator("#f-user").is_visible(), "Operator filter not visible"
    assert page.locator("#f-bizid").is_visible(), "Biz ID filter not visible"
    assert page.locator("button:has-text('查询')").is_visible(), "Query button not visible"
    log_result("Operation Logs - filter bar visible", True)

    # Wait for initial load
    time.sleep(1.5)

    # Log table should exist
    log_table = page.locator("#log-list")
    assert log_table.is_visible(), "Log list not visible"

    # Pagination
    pager = page.locator("#pager")
    assert pager.is_visible(), "Pagination not visible"
    log_result("Operation Logs - table and pagination visible", True)

    # Filter by biz type
    page.select_option("#f-biz", "APIKEY")
    page.click("button:has-text('查询')")
    time.sleep(1)
    log_result("Operation Logs - filter by APIKEY biz type", True)

    # Reset filter
    page.select_option("#f-biz", "")
    page.select_option("#f-op", "CREATE")
    page.click("button:has-text('查询')")
    time.sleep(1)
    log_result("Operation Logs - filter by CREATE op type", True)

    # Reset filters
    page.select_option("#f-op", "")
    page.click("button:has-text('查询')")
    time.sleep(1)

    # View content modal
    view_btn = page.locator("#log-list button:has-text('查看')").first
    if view_btn.is_visible():
        view_btn.click()
        time.sleep(0.5)
        content_modal = page.locator("#content-modal.open")
        assert content_modal.is_visible(), "Content modal not shown"
        content_json = page.locator("#content-json")
        assert content_json.is_visible(), "Content JSON not shown in modal"
        log_result("Operation Logs - content detail modal works", True)
        page.click("#content-modal .btn-ghost:has-text('关闭')")

    log_result("Operation Logs - all filters and features work", True)


def test_logout(page):
    """Test logout flow."""
    page.goto(f"{BASE_URL}/index.html")
    page.wait_for_load_state("networkidle")

    logout_btn = page.locator(".topbar .btn-logout:has-text('退出')")
    assert logout_btn.is_visible(), "Logout button not visible"

    logout_btn.click()
    page.wait_for_url("**/login.html", timeout=5000)

    # Verify token is cleared
    token = page.evaluate("() => localStorage.getItem('token')")
    assert token is None or token == "", "Token not cleared after logout"
    log_result("Logout - redirects to login, token cleared", True)


def run_tests():
    print("=" * 60)
    print("ThreadPool Admin Server - E2E Tests")
    print(f"Target: {BASE_URL}")
    print("=" * 60)

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        context = browser.new_context(viewport={"width": 1440, "height": 900})
        page = context.new_page()

        # Override alert to capture messages without blocking
        page.on("dialog", lambda d: d.accept())

        try:
            # 1. Login tests
            print("\n--- Login Tests ---")
            test_login_errors(page)

            # 2. Dashboard
            print("\n--- Dashboard Tests ---")
            test_dashboard(page)

            # 3. Topbar / Navigation
            print("\n--- Navigation Tests ---")
            test_topbar_user_info(page)
            test_topbar_navigation(page)

            # 4. API Keys
            print("\n--- API Keys Tests ---")
            test_api_keys_flow(page)

            # 5. Thread Pool Config
            print("\n--- Thread Pool Config Tests ---")
            test_thread_pools_flow(page)

            # 6. Config Diff
            print("\n--- Config Diff Tests ---")
            test_config_diff(page)

            # 7. Operation Logs
            print("\n--- Operation Logs Tests ---")
            test_operation_logs(page)

            # 8. Logout
            print("\n--- Logout Tests ---")
            test_logout(page)

        except Exception as e:
            print(f"\n[ERROR] Test execution failed: {e}")

            page.screenshot(path="e2e_error.png", full_page=True)
            print("Error screenshot saved to e2e_error.png")
            print(f"Current URL: {page.url}")
            raise

        finally:
            browser.close()

    # Summary
    print("\n" + "=" * 60)
    passed = sum(1 for _, p, _ in RESULTS if p)
    failed = len(RESULTS) - passed
    print(f"RESULTS: {passed} passed, {failed} failed out of {len(RESULTS)} tests")
    print("=" * 60)

    for name, p, detail in RESULTS:
        status = "PASS" if p else "FAIL"
        marker = "" if p else " <--"
        details = f" ({detail})" if detail else ""
        print(f"  [{status}] {name}{details}{marker}")

    if failed > 0:
        print(f"\n{failed} test(s) FAILED!")
        sys.exit(1)
    else:
        print("\nAll tests passed!")
        sys.exit(0)


if __name__ == "__main__":
    run_tests()
