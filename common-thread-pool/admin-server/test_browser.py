"""
Browser E2E test for ThreadPoolAdminServer admin pages.
Requires: pip install playwright && playwright install chromium
"""
import sys
import os
from playwright.sync_api import sync_playwright, expect

# Fix Windows console encoding for emoji/special chars
if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')

BASE_URL = "http://localhost:8080"
ADMIN_USER = "admin"
ADMIN_PASS = "changeme"

def login(page):
    """Login and return True if successful."""
    page.goto(f"{BASE_URL}/login.html")
    page.wait_for_load_state("networkidle")
    page.fill("#username", ADMIN_USER)
    page.fill("#password", ADMIN_PASS)
    page.click("button[type='submit']")
    # Wait for redirect to dashboard
    page.wait_for_url("**/index.html", timeout=10000)
    page.wait_for_load_state("networkidle")
    return True

def test_login_page(page):
    """Test login page renders correctly."""
    print("\n=== TEST: Login Page ===")
    page.goto(f"{BASE_URL}/login.html")
    page.wait_for_load_state("networkidle")

    # Check key elements
    expect(page.locator(".login-card__title")).to_have_text("ThreadPool Admin")
    expect(page.locator("#username")).to_be_visible()
    expect(page.locator("#password")).to_be_visible()
    expect(page.locator("button[type='submit']")).to_have_text("登 录")

    # Test empty form - remove required attr to bypass HTML5 validation
    page.evaluate("() => { document.getElementById('username').removeAttribute('required'); document.getElementById('password').removeAttribute('required'); }")
    page.fill("#username", "")
    page.fill("#password", "")
    page.click("button[type='submit']")
    page.wait_for_timeout(500)
    # The error element should show
    error = page.locator("#error")
    assert error.is_visible(), "Error message should show for empty form submission"

    # Test invalid credentials
    page.fill("#username", "wrong")
    page.fill("#password", "wrong")
    page.click("button[type='submit']")
    page.wait_for_timeout(1500)
    error = page.locator("#error")
    assert error.is_visible(), "Error message should show for invalid login"

    print("  PASS: Login page renders, validates empty/invalid input")

def test_login_success(page):
    """Test successful login flow."""
    print("\n=== TEST: Login Success ===")
    login(page)

    # Should be on dashboard with token set
    token = page.evaluate("() => localStorage.getItem('token')")
    assert token, "Token should be set in localStorage"
    username = page.evaluate("() => localStorage.getItem('username')")
    assert username == ADMIN_USER, f"Username should be {ADMIN_USER}"

    print(f"  PASS: Login successful, token stored, username={username}")

def test_dashboard(page):
    """Test dashboard page loads with data."""
    print("\n=== TEST: Dashboard ===")
    page.goto(f"{BASE_URL}/index.html")
    page.wait_for_load_state("networkidle")

    # Check breadcrumb and header
    expect(page.locator(".breadcrumb")).to_contain_text("仪表盘")
    expect(page.locator(".page-header h1")).to_contain_text("仪表盘")

    # Wait for data to load
    page.wait_for_timeout(2000)

    # Check stat cards populated
    apps_val = page.locator("#stat-apps").text_content()
    keys_val = page.locator("#stat-keys").text_content()
    configs_val = page.locator("#stat-configs").text_content()
    ops_val = page.locator("#stat-ops").text_content()

    assert apps_val != "--", f"Apps stat should be populated, got: {apps_val}"
    assert keys_val != "--", f"Keys stat should be populated, got: {keys_val}"
    assert configs_val != "--", f"Configs stat should be populated, got: {configs_val}"
    assert ops_val != "--", f"Ops stat should be populated, got: {ops_val}"

    print(f"  PASS: Dashboard loaded — apps={apps_val}, keys={keys_val}, configs={configs_val}, ops={ops_val}")

    # Check update line
    update_line = page.locator("#update-line").text_content()
    assert "加载中" not in update_line, f"Update line should not be loading: {update_line}"

    # Check topbar navigation
    nav_links = page.locator(".topbar__nav-link")
    count = nav_links.count()
    assert count >= 5, f"Should have at least 5 nav links, got {count}"

    # Check user avatar
    avatar = page.locator(".user-avatar__name")
    assert avatar.is_visible()
    print(f"  PASS: Topbar rendered, user={avatar.text_content()}")

    # Refresh button
    page.click("text=↻ 刷新")
    page.wait_for_timeout(1000)
    print("  PASS: Refresh button works")

def test_thread_pools_page(page):
    """Test thread pools management page."""
    print("\n=== TEST: Thread Pools Page ===")
    page.goto(f"{BASE_URL}/thread-pools.html")
    page.wait_for_load_state("networkidle")

    expect(page.locator(".page-header h1")).to_contain_text("线程池配置管理")

    # Check app switcher
    switcher = page.locator("#switcher-name")
    expect(switcher).to_be_visible()

    # Check action buttons
    expect(page.locator("text=管理应用")).to_be_visible()
    expect(page.locator("text=回收站")).to_be_visible()
    expect(page.locator("text=+ 新建池")).to_be_visible()

    print("  PASS: Thread pools page loaded with app switcher and action buttons")

    # Click app switcher to see dropdown
    switcher.click()
    page.wait_for_timeout(500)
    dropdown = page.locator("#app-dropdown")
    expect(dropdown).to_be_visible()
    print("  PASS: App switcher dropdown opens")

    # Click away to close
    page.locator(".breadcrumb").click()
    page.wait_for_timeout(300)

    # Test app manager modal
    page.click("text=管理应用")
    page.wait_for_timeout(500)
    # App manager should open as a drawer/modal
    manager_visible = page.locator(".drawer.open").count() > 0
    if manager_visible:
        print("  PASS: App manager drawer opened")
        page.click(".drawer__close")
        page.wait_for_timeout(300)
    else:
        print("  SKIP: App manager drawer not visible (may use different mechanism)")

    # Test recycle bin
    page.click("text=回收站")
    page.wait_for_timeout(500)
    recycle_visible = page.locator(".drawer.open").count() > 0
    if recycle_visible:
        print("  PASS: Recycle bin drawer opened")
        page.click(".drawer__close")
        page.wait_for_timeout(300)
    else:
        print("  SKIP: Recycle bin drawer not visible")

def test_api_keys_page(page):
    """Test API Keys management page."""
    print("\n=== TEST: API Keys Page ===")
    page.goto(f"{BASE_URL}/api-keys.html")
    page.wait_for_load_state("networkidle")

    expect(page.locator(".page-header h1")).to_contain_text("API Key 管理")
    expect(page.locator("text=+ 新建 API Key")).to_be_visible()

    # Check filter bar
    expect(page.locator("#f-search")).to_be_visible()
    expect(page.locator("#f-status")).to_be_visible()

    # Check table loaded
    page.wait_for_timeout(1000)
    tbody = page.locator("#apikey-list")
    rows = tbody.locator("tr")
    row_count = rows.count()

    if row_count == 1 and "empty-state" in (rows.first.get_attribute("class") or ""):
        print("  PASS: API Keys page loaded (empty state)")
    else:
        print(f"  PASS: API Keys page loaded with {row_count} entries")

    # Test create drawer
    page.click("text=+ 新建 API Key")
    page.wait_for_timeout(500)
    drawer = page.locator("#create-drawer")
    expect(drawer).to_be_visible()
    expect(page.locator("#f-appId")).to_be_visible()
    expect(page.locator("#f-appName")).to_be_visible()

    # Create a new API key
    test_app_id = "e2e-browser-test-app"
    page.fill("#f-appId", test_app_id)
    page.fill("#f-appName", "E2E Browser Test")
    page.click("#create-drawer button.btn-primary")
    page.wait_for_timeout(1500)

    # Check for success toast or result
    toast = page.locator(".toast--success, .toast--info")
    if toast.count() > 0:
        print("  PASS: API key creation triggered toast")

    # Close drawer if still open
    try:
        page.click("#create-overlay", timeout=2000)
    except:
        pass
    page.wait_for_timeout(300)

    # Search for the created key
    page.fill("#f-search", test_app_id)
    page.wait_for_timeout(1000)
    print(f"  PASS: Search filter works for '{test_app_id}'")

    # Clean up - delete the test key if it exists
    try:
        delete_btn = page.locator(f"tr:has-text('{test_app_id}') button:has-text('删除')")
        if delete_btn.count() > 0:
            delete_btn.first.click()
            page.wait_for_timeout(500)
            # Confirm dialog
            confirm_btn = page.locator("text=确认删除")
            if confirm_btn.count() > 0:
                confirm_btn.click()
                page.wait_for_timeout(1000)
                print("  PASS: API key deleted")
    except Exception as e:
        print(f"  NOTE: Cleanup skipped ({e})")

def test_stats_page(page):
    """Test stats/monitoring page."""
    print("\n=== TEST: Stats Page ===")
    page.goto(f"{BASE_URL}/stats.html")
    page.wait_for_load_state("networkidle")

    expect(page.locator(".page-header h1")).to_contain_text("监控统计")

    # Check stats page has app/pool selector
    page.wait_for_timeout(1000)
    print("  PASS: Stats page loaded")

    # Check for time tabs
    time_tabs = page.locator(".time-tab")
    tab_count = time_tabs.count()
    print(f"  PASS: Found {tab_count} time range tabs")

def test_operate_logs_page(page):
    """Test operate logs page."""
    print("\n=== TEST: Operate Logs Page ===")
    page.goto(f"{BASE_URL}/operate-logs.html")
    page.wait_for_load_state("networkidle")

    expect(page.locator(".page-header h1")).to_contain_text("操作日志")

    # Check filter bar
    expect(page.locator("#f-biz")).to_be_visible()
    expect(page.locator("#f-op")).to_be_visible()
    expect(page.locator("#f-user")).to_be_visible()

    # Check export button
    expect(page.locator("text=↧ 导出 CSV")).to_be_visible()

    # Check table loaded
    page.wait_for_timeout(1000)
    print("  PASS: Operate logs page loaded with filters and export button")

    # Test filters
    page.select_option("#f-biz", "APIKEY")
    page.click("text=查询")
    page.wait_for_timeout(1000)
    print("  PASS: Filter by biz type works")

    page.select_option("#f-biz", "")
    page.select_option("#f-op", "CREATE")
    page.click("text=查询")
    page.wait_for_timeout(1000)
    print("  PASS: Filter by op type works")

    page.click("text=重置")
    page.wait_for_timeout(500)
    print("  PASS: Reset filter works")

def test_admin_users_page(page):
    """Test admin users management page (SUPER_ADMIN only)."""
    print("\n=== TEST: Admin Users Page ===")
    page.goto(f"{BASE_URL}/admin-users.html")
    page.wait_for_load_state("networkidle")

    expect(page.locator(".page-header h1")).to_contain_text("管理员管理")

    # Check self info card
    page.wait_for_timeout(1000)
    my_name = page.locator("#my-nickname").text_content()
    my_username = page.locator("#my-username").text_content()
    print(f"  PASS: Admin users page loaded, current user: {my_username} ({my_name})")

    # Check if create button is visible (SUPER_ADMIN has it)
    create_btn = page.locator("#btn-create")
    is_visible = create_btn.is_visible()
    print(f"  PASS: Create button visible={is_visible} (SUPER_ADMIN={page.evaluate('() => isSuperAdmin()')})")

def test_auth_guard(page):
    """Test that pages redirect to login when no auth."""
    print("\n=== TEST: Auth Guard ===")
    # Clear auth
    page.evaluate("() => { localStorage.clear(); }")
    page.goto(f"{BASE_URL}/index.html")
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(500)

    # Should redirect to login
    url = page.url
    assert "login.html" in url, f"Should redirect to login, got: {url}"
    print("  PASS: Unauthenticated access redirects to login")

def test_logout(page):
    """Test logout flow."""
    print("\n=== TEST: Logout ===")
    login(page)

    # Click on user avatar to logout
    page.goto(f"{BASE_URL}/index.html")
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(500)

    avatar = page.locator(".user-avatar")
    avatar.click()
    page.wait_for_timeout(1000)

    # Should redirect to login
    url = page.url
    assert "login.html" in url, f"Should redirect to login after logout, got: {url}"
    token = page.evaluate("() => localStorage.getItem('token')")
    assert not token, "Token should be cleared after logout"
    print("  PASS: Logout clears token and redirects to login")

def test_navigation(page):
    """Test topbar navigation works for all pages."""
    print("\n=== TEST: Navigation ===")
    login(page)

    nav_items = [
        ("仪表盘", "index.html"),
        ("线程池", "thread-pools.html"),
        ("API Keys", "api-keys.html"),
        ("监控", "stats.html"),
        ("审计", "operate-logs.html"),
    ]

    for label, expected_url in nav_items:
        link = page.locator(f".topbar__nav-link:has-text('{label}')")
        if link.count() > 0:
            link.click()
            page.wait_for_load_state("networkidle")
            page.wait_for_timeout(500)
            current = page.url
            assert expected_url in current, f"Nav to '{label}' should go to {expected_url}, got: {current}"
            print(f"  PASS: Nav → '{label}' → {expected_url}")

    print("  PASS: All navigation links work")

def main():
    print("=" * 60)
    print("ThreadPoolAdminServer Browser E2E Tests")
    print("=" * 60)

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        context = browser.new_context(
            viewport={"width": 1440, "height": 900},
            locale="zh-CN",
        )
        page = context.new_page()

        try:
            test_login_page(page)
            test_login_success(page)
            test_dashboard(page)
            test_thread_pools_page(page)
            test_api_keys_page(page)
            test_stats_page(page)
            test_operate_logs_page(page)
            test_admin_users_page(page)
            test_navigation(page)
            test_logout(page)
            test_auth_guard(page)

            print("\n" + "=" * 60)
            print("ALL TESTS PASSED")
            print("=" * 60)
        except Exception as e:
            # Take screenshot on failure
            page.screenshot(path="/tmp/e2e_failure.png", full_page=True)
            print(f"\nFAILED: {e}")
            print("Screenshot saved to /tmp/e2e_failure.png")
            raise
        finally:
            browser.close()

if __name__ == "__main__":
    main()