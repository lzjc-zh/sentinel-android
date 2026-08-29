import json, urllib.request, urllib.error

# Coding Plan API Key from settings (用户有2套key)
coding_api_key = input("Enter Coding Plan API Key (Bearer): ").strip()

endpoints = [
    ("POST", "https://arkbff-cn-beijing.console.volcengine.com/api/2024-10-01/GetCodingPlanQuota"),
    ("POST", "https://ark.cn-beijing.volcengineapi.com/?Action=GetCodingPlanUsage&Version=2024-01-01"),
    ("GET", "https://ark.cn-beijing.volces.com/api/coding/v3/models"),
    ("GET", "https://ark.cn-beijing.volces.com/api/coding/v1/usage"),
    ("POST", "https://ark.cn-beijing.volces.com/api/coding/v3/usage"),
]

for method, url in endpoints:
    try:
        req = urllib.request.Request(url, method=method)
        req.add_header("Authorization", f"Bearer {coding_api_key}")
        req.add_header("Content-Type", "application/json")
        if method == "POST":
            req.data = b"{}"
        resp = urllib.request.urlopen(req, timeout=5)
        body = resp.read().decode()
        print(f"✅ {method} {url}: {resp.status}")
        print(f"   {body[:500]}")
    except urllib.error.HTTPError as e:
        body = e.read().decode()[:300]
        print(f"❌ {method} {url}: HTTP {e.code}")
        print(f"   {body}")
    except Exception as e:
        print(f"❌ {method} {url}: {e}")
