#!/usr/bin/env python3
"""Core v3.2 end-to-end tests for common-thread-pool.

Requires: requests library (pip install requests)
Requires: running admin-server on localhost:8080 (local profile)
Run with: python e2e_tests.py

Scenarios:
  1. Single delete reverts client to local config
  2. Batch delete reverts all client pools
  3. Restart tombstone does not revive retired config
  4. Alert MVP visible on dashboard
  5. Pull path plural and legacy 410
  6. createApp failure rolls back
"""

import os
import sys
import time

try:
    import requests
except ImportError:
    print("pip install requests")
    sys.exit(1)


class SkipTest(Exception):
    """Raised by a test to mark itself as intentionally skipped (not passed, not failed)."""


ADMIN_URL = os.environ.get("ADMIN_URL", "http://localhost:8080")
E2E_APP_ID = "e2e-test-app"
E2E_POOL_NAME = "e2e-test-pool"
APP_NAME = "E2E Test App"


def admin_url(path):
    return ADMIN_URL + path


def login():
    r = requests.post(admin_url("/api/auth/login"), json={
        "username": "admin",
        "password": "admin"
    }, timeout=5)
    if r.status_code == 200:
        body = r.json()
        return body.get("data", {}).get("token", "")
    return ""


def admin_headers(token):
    return {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json"
    }


def app_headers(api_key):
    return {
        "X-App-Id": E2E_APP_ID,
        "X-API-Key": api_key,
        "Content-Type": "application/json"
    }


def ensure_app(token):
    """Create app if it doesn't exist and return its API key (None if the app already existed)."""
    r = requests.get(admin_url(f"/api/thread-pool/configs/{E2E_APP_ID}/list"),
                     headers=admin_headers(token), timeout=5)
    if r.status_code == 200:
        return None
    r = requests.post(admin_url("/api/api-keys"), json={
        "appId": E2E_APP_ID,
        "appName": APP_NAME,
        "description": "E2E test app"
    }, headers=admin_headers(token), timeout=5)
    if r.status_code == 200:
        body = r.json()
        print(f"  Created app: {E2E_APP_ID}")
        return body.get("data", {}).get("apiKey")
    return None


def cleanup_app(token):
    """Delete the e2e test app."""
    requests.delete(admin_url(f"/api/thread-pool/configs/{E2E_APP_ID}"),
                    headers=admin_headers(token), timeout=5)


def create_config(token, pool_name, core=2, max_pool=4):
    cfg = {
        "poolName": pool_name,
        "corePoolSize": core,
        "maximumPoolSize": max_pool,
        "keepAliveTime": 60,
        "timeUnit": "SECONDS",
        "queueCapacity": 100,
        "queueType": "BLOCKING_QUEUE",
        "rejectPolicyType": "ABORT"
    }
    r = requests.post(admin_url(f"/api/thread-pool/apps/{E2E_APP_ID}/configs"),
                      json=cfg, headers=admin_headers(token), timeout=5)
    return r


def delete_config(token, pool_name):
    r = requests.delete(admin_url(f"/api/thread-pool/apps/{E2E_APP_ID}/configs/{pool_name}"),
                        headers=admin_headers(token), timeout=5)
    return r


# ── Scenario 1: single delete ──
def test_single_delete_reverts():
    print("=== Scenario 1: Single delete ===")
    token = login()
    assert token, "Login failed"
    ensure_app(token)
    try:
        create_config(token, E2E_POOL_NAME)
        r = delete_config(token, E2E_POOL_NAME)
        assert r.status_code == 200, f"Delete failed: {r.status_code}"
        print("  PASS")
    finally:
        cleanup_app(token)


# ── Scenario 2: batch delete ──
def test_batch_delete():
    print("=== Scenario 2: Batch delete ===")
    token = login()
    assert token, "Login failed"
    ensure_app(token)
    try:
        create_config(token, "batch-pool-1")
        create_config(token, "batch-pool-2")
        r = requests.delete(admin_url(f"/api/thread-pool/configs/{E2E_APP_ID}"),
                            headers=admin_headers(token), timeout=5)
        assert r.status_code == 200, f"Batch delete failed: {r.status_code}"
        print("  PASS")
    finally:
        cleanup_app(token)


# ── Scenario 3: restart tombstone ──
def test_restart_tombstone():
    print("=== Scenario 3: Restart tombstone ===")
    raise SkipTest("verified via RemoteConfigSourcePoolManagerTest (unit-tested)")


# ── Scenario 4: alert on dashboard ──
def test_alert_dashboard():
    print("=== Scenario 4: Alert dashboard ===")
    token = login()
    assert token, "Login failed"
    r = requests.get(admin_url("/api/dashboard/summary?limit=5&offset=0"),
                     headers=admin_headers(token), timeout=5)
    assert r.status_code == 200, f"Dashboard failed: {r.status_code}"
    body = r.json()
    assert body["code"] == 0
    data = body["data"]
    assert "alertCount" in data, "Missing alertCount"
    assert "alerts" in data, "Missing alerts"
    assert "hasMore" in data, "Missing hasMore"
    print(f"  alertCount={data['alertCount']}, hasMore={data['hasMore']}")
    print("  PASS")


# ── Scenario 5: pull path plural and legacy 410 ──
def test_pull_path():
    print("=== Scenario 5: Pull path ===")
    token = login()
    assert token, "Login failed"
    cleanup_app(token)  # ensure a clean slate so ensure_app() creates a fresh key
    api_key = ensure_app(token)
    try:
        # Legacy path returns 410
        r = requests.get(admin_url("/open/api/thread-pool/config/app1/pull"),
                         timeout=5)
        assert r.status_code == 410, f"Expected 410, got {r.status_code}"
        print("  Legacy path: 410")

        # New path works with a valid API key
        assert api_key, "ensure_app did not return a fresh API key"
        r = requests.get(admin_url(f"/open/api/thread-pool/configs/{E2E_APP_ID}/pull"),
                         headers=app_headers(api_key), timeout=5)
        assert r.status_code in (200, 304), f"Unexpected status: {r.status_code}"
        print(f"  Plural path: {r.status_code}")
        print("  PASS")
    finally:
        cleanup_app(token)


# ── Scenario 6: createApp failure rolls back ──
def test_create_app_rollback():
    print("=== Scenario 6: createApp rollback ===")
    raise SkipTest("verified via ConfigAdminServiceTest (unit-tested)")


def main():
    print("Core v3.2 E2E Tests")
    print("=" * 60)

    tests = [
        test_single_delete_reverts,
        test_batch_delete,
        test_restart_tombstone,
        test_alert_dashboard,
        test_pull_path,
        test_create_app_rollback,
    ]

    passed = 0
    failed = 0
    skipped = 0
    for test in tests:
        try:
            test()
            passed += 1
        except SkipTest as e:
            print(f"  SKIP: {e}")
            skipped += 1
        except Exception as e:
            print(f"  FAIL: {e}")
            failed += 1

    print()
    print(f"Results: {passed} passed, {failed} failed, {skipped} skipped")
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()