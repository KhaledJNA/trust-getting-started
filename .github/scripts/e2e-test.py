#!/usr/bin/env python3
"""End-to-end test of the demo, as a browser would use it (standard library
only). Needs a fresh stack: the step-up registers an OTP for sample.user on
first use, so run `docker compose down -v` afterwards before trying the demo
by hand.

    python3 .github/scripts/e2e-test.py      # exit code 0 when every check passes
"""
import os, sys, re, html, json, urllib.request, urllib.parse, http.cookiejar, hmac, hashlib, struct, time, base64

BFF = os.environ.get("TRUST_BFF_URL", "http://localhost:9081")

class Policy(http.cookiejar.DefaultCookiePolicy):
    # Keycloak marks its cookies Secure even over http://localhost.
    def return_ok_secure(self, cookie, request): return True
    def set_ok(self, cookie, request): return True

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *a, **k): return None

class Browser:
    def __init__(self):
        self.jar = http.cookiejar.CookieJar(Policy())
        self.op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar), NoRedirect)
    def req(self, method, url, data=None, headers=None, follow=True):
        while True:
            body = urllib.parse.urlencode(data).encode() if isinstance(data, dict) else data
            r = urllib.request.Request(url, data=body, method=method, headers=headers or {})
            try:
                resp = self.op.open(r); status = resp.status; hdrs = resp.headers; text = resp.read().decode()
            except urllib.error.HTTPError as e:
                status = e.code; hdrs = e.headers; text = e.read().decode()
            if follow and status in (301, 302, 303, 307):
                url = urllib.parse.urljoin(url, hdrs["Location"]); method = "GET"; data = None; headers = {}
                continue
            return status, hdrs, text, url
    def cookie(self, name):
        return next((c.value for c in self.jar if c.name == name), "")
    def api(self, method, path):
        h = {"Accept": "application/json", "X-XSRF-TOKEN": urllib.parse.unquote(self.cookie("XSRF-TOKEN"))}
        return self.req(method, BFF + path, data=b"" if method != "GET" else None, headers=h, follow=False)

def form_action(page):
    m = re.search(r'<form[^>]*action="([^"]+)"', page)
    return html.unescape(m.group(1)) if m else None

def totp(secret):
    key = secret.encode()  # Keycloak's totpSecret form field is the raw key
    h = hmac.new(key, struct.pack(">Q", int(time.time()) // 30), hashlib.sha1).digest()
    o = h[-1] & 15
    return "%06d" % ((struct.unpack(">I", h[o:o+4])[0] & 0x7fffffff) % 1000000)

def login(b, user):
    s, h, page, url = b.req("GET", BFF + "/oauth2/authorization/trust-sample")
    s, h, page, url = b.req("POST", form_action(page), {"username": user, "password": "sample"})
    assert url.startswith(BFF), f"login failed for {user}: {url} {page[:300]}"
    b.req("GET", BFF + "/api/session")  # issues the CSRF cookie

results = []
def check(label, got, expected, extra=""):
    ok = got == expected
    results.append(ok)
    print(f"{'OK ' if ok else 'KO '} {label}: {got} (expected {expected}) {extra}")

def short(text):
    try: return json.dumps(json.loads(text), separators=(",", ":"))[:220]
    except Exception: return text[:160].replace("\n", " ")

# --- sample.user, acr=basic
u = Browser(); login(u, "sample.user")
s, _, t, _ = u.api("GET", "/api/session"); check("session", json.loads(t)["acr"], "basic", short(t))
for m, p, e in [("GET", "/api/me", 200), ("GET", "/api/reader", 200), ("GET", "/api/documents/1", 200),
                ("PUT", "/api/documents/1", 403), ("GET", "/api/downstream/direct", 200),
                ("GET", "/api/downstream/on-behalf", 200), ("GET", "/api/downstream/system", 200),
                ("GET", "/api/async/me", 200)]:
    s, h, t, _ = u.api(m, p); check(f"user {m} {p}", s, e, short(t))
for p in ["/api/sensitive", "/api/local/strong"]:
    s, h, t, _ = u.api("GET", p); check(f"user GET {p}", s, 401, h.get("WWW-Authenticate", ""))

# --- step-up to acr=strong (OTP; configured on first use)
s, h, page, url = u.req("GET", BFF + "/oauth2/step-up?acr=strong&returnTo=/")
for _ in range(3):
    if url.startswith(BFF): break
    if 'totpSecret' in page:
        sec = re.search(r'name="totpSecret" value="([^"]+)"', page).group(1)
        u.secret = sec
        s, h, page, url = u.req("POST", form_action(page), {"totp": totp(sec), "totpSecret": sec, "userLabel": "e2e"})
    elif hasattr(u, "secret"):
        s, h, page, url = u.req("POST", form_action(page), {"otp": totp(u.secret)})
    else:
        sys.exit("sample.user already has an OTP from an earlier run: start from a fresh stack (docker compose down -v).")
check("step-up returns to the BFF", url.startswith(BFF), True, url)
s, _, t, _ = u.api("GET", "/api/session"); check("session after step-up", json.loads(t)["acr"], "strong")
for p in ["/api/sensitive", "/api/local/strong"]:
    s, h, t, _ = u.api("GET", p); check(f"strong GET {p}", s, 200, short(t))

# --- logout
s, h, t, _ = u.req("POST", BFF + "/logout", {"_csrf": urllib.parse.unquote(u.cookie("XSRF-TOKEN"))}, follow=False)
check("logout redirects to Keycloak", "/protocol/openid-connect/logout" in h.get("Location", ""), True, h.get("Location", "")[:90])
s, _, t, _ = u.api("GET", "/api/session"); check("session after logout", json.loads(t)["authenticated"], False)

# --- sample.manager
m = Browser(); login(m, "sample.manager")
for meth, p, e in [("PUT", "/api/documents/1", 200), ("PUT", "/api/documents/2", 403), ("GET", "/api/reader", 403)]:
    s, h, t, _ = m.api(meth, p); check(f"manager {meth} {p}", s, e, short(t))

# --- sample.denied
d = Browser(); login(d, "sample.denied")
for meth, p, e in [("GET", "/api/me", 403), ("GET", "/api/documents/1", 403), ("GET", "/api/downstream/direct", 403)]:
    s, h, t, _ = d.api(meth, p); check(f"denied {meth} {p}", s, e, short(t))

print(f"\n{sum(results)}/{len(results)} checks passed")
sys.exit(0 if all(results) else 1)
