"""
Mock stats data injection script.
Usage: python mock_stats.py
Requires admin-server running on localhost:8080 with default admin/changeme credentials.
"""
import json, urllib.request, sys

BASE = "http://localhost:8080"

class Api:
    def __init__(self, token):
        self.token = token
    def _call(self, method, path, body=None):
        headers = {"Content-Type": "application/json", "Authorization": f"Bearer {self.token}"}
        data = json.dumps(body).encode() if body else None
        req = urllib.request.Request(f"{BASE}{path}", data=data, headers=headers, method=method)
        try:
            resp = urllib.request.urlopen(req)
            return json.loads(resp.read())
        except urllib.error.HTTPError as e:
            return json.loads(e.read())

def login():
    data = json.dumps({"username": "admin", "password": "changeme"}).encode()
    req = urllib.request.Request(f"{BASE}/api/auth/login", data=data,
        headers={"Content-Type": "application/json"}, method="POST")
    resp = urllib.request.urlopen(req)
    r = json.loads(resp.read())
    return r["data"]["token"]

# Login
token = login()
api = Api(token)
print("[OK] Login")

# Create app
r = api._call("POST", "/api/thread-pool/apps",
    {"appId": "demo-app", "appName": "Demo App"})
status = "OK" if r.get("code") == 0 else r.get("message", str(r))
print(f"[{status}] Create app")

# Create pool configs
pools = [
    {"poolName": "order-pool", "corePoolSize": 10, "maximumPoolSize": 20, "queueType": "LINKED_BLOCKING_QUEUE",
     "queueCapacity": 200, "rejectPolicyType": "ABORT", "keepAliveTime": 60, "timeUnit": "SECONDS", "threadNamePrefix": "order"},
    {"poolName": "user-pool",  "corePoolSize": 8,  "maximumPoolSize": 16, "queueType": "LINKED_BLOCKING_QUEUE",
     "queueCapacity": 150, "rejectPolicyType": "CALLER_RUNS", "keepAliveTime": 30, "timeUnit": "SECONDS", "threadNamePrefix": "user"},
    {"poolName": "task-pool",  "corePoolSize": 5,  "maximumPoolSize": 10, "queueType": "ARRAY_BLOCKING_QUEUE",
     "queueCapacity": 500, "rejectPolicyType": "ABORT", "keepAliveTime": 120, "timeUnit": "SECONDS", "threadNamePrefix": "task"},
]
for p in pools:
    r = api._call("POST", f"/api/thread-pool/configs/demo-app/{p['poolName']}", p)
    s = "OK" if r.get("code") == 0 else r.get("message", str(r))
    print(f"[{s}] Create pool: {p['poolName']}")

# Mock stats
for pool_name, count in [("order-pool", 40), ("user-pool", 30), ("task-pool", 20)]:
    r = api._call("POST", f"/api/stats/mock?appId=demo-app&poolName={pool_name}&count={count}")
    s = "OK" if r and r.get("code") == 0 else str(r)
    print(f"[{s}] Mock {count} stats for {pool_name}")

print("\nDone. Visit http://localhost:8080/stats.html")
