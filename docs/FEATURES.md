# Core Features

## 1. Dynamic Route Management

**Storage:** Nacos `gateway-routes.json`

- Add route: configure route ID, target URI, predicates (Path / Method / Header), filters (StripPrefix etc.)
- Delete route: **gateway returns HTTP 404 immediately — no restart required**
- Query all routes

**Real-time effective mechanism:**
1. Admin publishes new config to Nacos
2. `NacosRouteDefinitionLocator` detects change → clears local route cache (TTL 10 s)
3. Publishes `RefreshRoutesEvent` → forces SCG's `CachingRouteLocator` to **rebuild route table immediately**
4. Next request uses the new route (or 404 if deleted)

**Supported URI formats:**

| Format | Description |
|--------|-------------|
| `static://service-name` | Static node list — no Nacos service registry required |
| `lb://service-name` | Nacos service discovery with load balancing |
| `http://ip:port` | Direct connection to a fixed address |

---

## 2. Static Service Management (`static://` Protocol)

**Storage:** Nacos `gateway-services.json`

For legacy or external services not registered in Nacos — configure IP + Port node list directly.

- Add service: configure service name, instance list (IP / Port / Weight), load balancing strategy
- Delete service: **effective immediately** (requests fail if no nodes found)
- Dynamically modify instance weight / add / remove nodes

**Load balancing strategies** (`StaticProtocolGlobalFilter`, order 10001):

| Strategy | Algorithm | Notes |
|----------|-----------|-------|
| `round-robin` | Round-robin | Default — timestamp modulo |
| `weighted` | **Deterministic weighted round-robin** | `AtomicLong` counter + expanded slot list — strictly guarantees weight ratios |
| `random` | Random | `Random.nextInt()` |

> Example: weight 1 : 2 → every 3 requests: 1 to Instance A, 2 to Instance B (deterministic, not probabilistic).

Nacos config change → service cache cleared immediately (no TTL delay).

---

## 3. Nacos Discovery Load Balancing (`lb://` Protocol)

**Implementation:** `NacosLoadBalancerFilter` (order 10150) — replaces SCG's default `ReactiveLoadBalancerClientFilter`

- Queries healthy + enabled instance list from Nacos naming service
- Supports weighted round-robin instance selection
- Resolves `lb://service-name` → real `http://ip:port`

---

## 4. Plugin System (Dynamic Hot Update)

**Storage:** Nacos `gateway-plugins.json` — all plugins managed in a single config.

When the Nacos config is deleted, the gateway's in-memory plugin cache is **cleared immediately** — plugins stop working instantly.

---

### 4.1 Rate Limiter Plugin (order −50)

**Implementation:** `RateLimiterGlobalFilter` + `RedisRateLimiter`

- Redis **ZSET sliding time window** — accurate QPS control (no burst error vs token bucket)
- Independent configuration per route
- Multiple rate limit key dimensions (`keyType`):

| `keyType` | Behavior |
|-----------|----------|
| `ip` | Count per client IP |
| `route` | Shared count for the entire route |
| `combined` | Route + IP combination |
| `header` | Count by specified request header value |

- Time units: `second` / `minute` / `hour`
- Exceed limit → **HTTP 429 Too Many Requests** with headers:
  - `X-RateLimit-Limit` — configured threshold
  - `X-RateLimit-Remaining` — remaining requests
  - `X-RateLimit-Type` — limiter type (`redis`)

**Config example:**
```json
{
  "routeId": "demo-route",
  "qps": 10,
  "timeUnit": "second",
  "burstCapacity": 20,
  "keyType": "ip",
  "keyPrefix": "rate_limit:",
  "enabled": true
}
```

---

### 4.2 IP Access Control Plugin (order −100)

**Implementation:** `IPFilterGlobalFilter`

- Modes: **whitelist** (allow only listed IPs) or **blacklist** (block listed IPs)
- IP matching supports 3 formats:
  - Exact IP: `192.168.1.100`
  - Wildcard: `192.168.1.*`
  - CIDR: `192.168.1.0/24`
- Client IP read from `X-Forwarded-For` first (reverse proxy compatible), then `RemoteAddress`
- Blocked → **HTTP 403 Forbidden**

**Config example:**
```json
{
  "routeId": "demo-route",
  "mode": "whitelist",
  "ipList": ["192.168.1.0/24", "127.0.0.1"],
  "enabled": true
}
```

---

### 4.3 Timeout Plugin (order −200)

**Implementation:** `TimeoutGlobalFilter`

- Per-route connect timeout and response timeout
- Injects values into SCG route metadata (`RouteMetadataUtils.CONNECT_TIMEOUT_ATTR` / `RESPONSE_TIMEOUT_ATTR`)
- `NettyRoutingFilter` reads them and applies at the **Netty `HttpClient` level** — genuine network-layer timeout
- Timeout → **HTTP 504 Gateway Timeout** (not 500)

| Field | Scope | On expiry |
|-------|-------|-----------|
| `connectTimeout` | TCP handshake | HTTP 504 |
| `responseTimeout` | Request sent → full response received | HTTP 504 |

**Config example:**
```json
{
  "routeId": "demo-route",
  "connectTimeout": 3000,
  "responseTimeout": 10000,
  "enabled": true
}
```
