# Spring Cloud Gateway Dynamic Management System

A production-ready dynamic API gateway management system built on **Spring Cloud Gateway 4.1**, **Spring Boot 3.2** and **Nacos 2.4.3**. Manage routes, services and plugins in real time through a clean web console — no YAML editing, no restarts required.

<div align="center">

[![Spring Cloud Gateway](https://img.shields.io/badge/Spring%20Cloud%20Gateway-4.1.0-blue)](https://spring.io/projects/spring-cloud-gateway)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-green)](https://spring.io/projects/spring-boot)
[![Nacos](https://img.shields.io/badge/Nacos-2.4.3-orange)](https://nacos.io/)
[![Java](https://img.shields.io/badge/Java-17-red)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

</div>

---

## ✨ Features

| Feature | Description |
|---------|-------------|
| 🖥️ **Web Admin Console** | Thymeleaf-based UI to manage routes, services and plugins |
| ⚡ **Real-time Hot Reload** | Config changes propagate to the gateway instantly via Nacos listeners + `RefreshRoutesEvent` |
| 🛣️ **Dynamic Routing** | Create / update / **delete** routes on the fly; supports Path, Host, Method, Header predicates |
| ⚖️ **Load Balancing** | Round-robin, **deterministic weighted round-robin**, and random strategies |
| 🔒 **IP Access Control** | Whitelist / blacklist mode per route; rejects with HTTP 403 |
| 🚦 **Rate Limiting** | Per-route request rate limiting (Sentinel / Redis backed) |
| 📨 **Custom Request Headers** | Inject headers dynamically; supports variable substitution (e.g. `${random.uuid}`) |
| ⏱️ **Per-route Timeout** | Independent connect-timeout and response-timeout per route; returns HTTP 504 on timeout |
| 🔄 **static:// Protocol** | Route to statically configured backend instances without a service registry |
| 🔍 **Nacos Discovery LB** | Native Nacos service discovery with weighted round-robin for `lb://` routes |

---

## 🏗️ Architecture

```
┌──────────────────────────────────────────────┐
│                    Client                    │
└────────────────────┬─────────────────────────┘
                     │ HTTP Request
                     ▼
┌──────────────────────────────────────────────┐
│                  My-Gateway                  │
│  ┌────────────────────────────────────────┐  │
│  │         Global Filter Chain            │  │
│  │  TimeoutGlobalFilter        (order -200)│  │
│  │  IPFilterGlobalFilter       (order -100)│  │
│  │  RateLimiterGlobalFilter    (order -50) │  │
│  │  DynamicCustomHeaderFilter  (order -10) │  │
│  │  NacosLoadBalancerFilter    (order 10150│  │
│  │  StaticProtocolGlobalFilter (order 10001│  │
│  └────────────────────────────────────────┘  │
│  Route Definitions ← NacosRouteDefinitionLocator│
└────────────────────┬─────────────────────────┘
                     │
                     ▼
         ┌───────────────────┐
         │   Backend Service  │
         └───────────────────┘

┌──────────────────────────────────────────────┐
│              Gateway Admin (8080)            │
│  RouteController / ServiceController /       │
│  PluginController  →  Nacos Config Center    │
└──────────────────────────────────────────────┘
```

**Config propagation flow:**
```
Gateway Admin  ──write──►  Nacos Config Center  ──listener──►  My-Gateway
                                                               (RefreshRoutesEvent / in-memory update)
```

---

## 🚀 Quick Start

### Prerequisites
- JDK 17+
- Maven 3.8+
- Nacos 2.4.3
- Redis 6.0+

### Step 1 — Start Dependencies

**Nacos (standalone mode):**
```bash
cd nacos/bin
# Linux / macOS
sh startup.sh -m standalone
# Windows
startup.cmd -m standalone
```

**Redis:**
```bash
redis-server
```

### Step 2 — Bootstrap Nacos Configurations

Create the following three config items in the Nacos console (Namespace: **public**, Group: **DEFAULT_GROUP**).

**`gateway-routes.json`**
```json
{
  "version": "1.0",
  "routes": [
    {
      "id": "demo-route",
      "uri": "static://demo-service",
      "predicates": [{"name": "Path", "args": {"pattern": "/api/**"}}],
      "filters": [{"name": "StripPrefix", "args": {"parts": "1"}}]
    }
  ]
}
```

**`gateway-services.json`**
```json
{
  "version": "1.0",
  "services": [
    {
      "name": "demo-service",
      "loadBalancer": "weighted",
      "instances": [
        {"ip": "127.0.0.1", "port": 9000, "weight": 1, "healthy": true, "enabled": true},
        {"ip": "127.0.0.1", "port": 9001, "weight": 2, "healthy": true, "enabled": true}
      ]
    }
  ]
}
```

**`gateway-plugins.json`**
```json
{
  "version": "1.0",
  "plugins": {
    "rateLimiters": [],
    "ipFilters": [],
    "timeouts": [
      {
        "routeId": "demo-route",
        "connectTimeout": 5000,
        "responseTimeout": 10000,
        "enabled": true
      }
    ]
  }
}
```

### Step 3 — Run Services

```bash
# Backend demo service — instance 1 (port 9000)
cd demo-service && mvn spring-boot:run

# Backend demo service — instance 2 (port 9001, open a new terminal)
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=9001"

# API Gateway
cd my-gateway && mvn spring-boot:run

# Admin Console
cd gateway-admin && mvn spring-boot:run
```

### Step 4 — Access

| Component | URL | Notes |
|-----------|-----|-------|
| **Admin Console** | http://localhost:8080 | Web UI for managing all config |
| **Gateway** | http://localhost:80 | API entry point |
| **Demo Service** | http://localhost:9000 | Example backend |
| **Nacos Console** | http://localhost:8848/nacos | Config & service registry |

---

## 🔧 Plugin Reference

### ⏱️ Timeout Plugin

Configures independent connect and response timeouts per route. On timeout the gateway returns **HTTP 504 Gateway Timeout**.

```json
{
  "routeId": "demo-route",
  "connectTimeout": 3000,
  "responseTimeout": 5000,
  "enabled": true
}
```

| Field | Description | Unit |
|-------|-------------|------|
| `connectTimeout` | TCP connection timeout | ms |
| `responseTimeout` | Total time from request sent to full response received | ms |

> **How it works:** `TimeoutGlobalFilter` (order = -200) injects values into SCG route metadata via `RouteMetadataUtils`. `NettyRoutingFilter` reads them and applies the timeouts at the Netty `HttpClient` level.

### 🚦 Rate Limiting Plugin

Per-route request rate limiting backed by Redis or Sentinel.

```json
{
  "routeId": "demo-route",
  "maxRequests": 100,
  "windowSeconds": 60,
  "enabled": true
}
```

### 🔒 IP Access Control Plugin

Whitelist or blacklist IPs / CIDR ranges per route. Blocked requests receive **HTTP 403 Forbidden**.

```json
{
  "routeId": "demo-route",
  "mode": "whitelist",
  "ipList": ["192.168.1.0/24", "127.0.0.1"],
  "enabled": true
}
```

### 📨 Custom Request Header Plugin

Inject arbitrary headers before forwarding to backends.

```json
{
  "routeId": "demo-route",
  "headers": {
    "X-Request-Id": "${random.uuid}",
    "X-Gateway-Version": "1.0"
  },
  "enabled": true
}
```

---

## ⚖️ Load Balancing Strategies

Set `loadBalancer` in `gateway-services.json` to one of:

| Value | Algorithm | Notes |
|-------|-----------|-------|
| `round-robin` | Round-robin | Default |
| `weighted` | **Deterministic weighted round-robin** | Strictly respects `weight` ratios using an `AtomicLong` counter |
| `random` | Random | Uniform random selection |

Example — weight ratio 1 : 2 guarantees every 3 requests: 1 → instance A, 2 → instance B.

---

## 🔄 Real-time Configuration Updates

All configuration changes are propagated to the gateway **without restart**:

| Change | Mechanism | Effective latency |
|--------|-----------|-------------------|
| Add / update / **delete route** | Nacos listener → `RefreshRoutesEvent` → SCG `CachingRouteLocator` rebuild | < 1 s |
| Add / update / delete service | Nacos listener → `StaticProtocolGlobalFilter` cache cleared | < 1 s |
| Add / update / delete plugin | Nacos listener → `PluginConfigManager` in-memory update | < 1 s |

> Deleting a route in the Admin Console will cause the gateway to return **HTTP 404** immediately for that path.

---

## 📁 Project Structure

```
scg-dynamic-admin-demo/
├── gateway-admin/                   # Web admin console (port 8080)
│   ├── controller/                  # REST API + Thymeleaf page controllers
│   ├── model/                       # Config models: RouteDefinition, ServiceDefinition, PluginConfig …
│   ├── service/                     # Business logic: RouteService, ServiceManager, PluginService …
│   └── resources/templates/         # Thymeleaf HTML templates
├── my-gateway/                      # SCG gateway core (port 80)
│   ├── filter/
│   │   ├── TimeoutGlobalFilter.java          # Per-route timeout (order -200)
│   │   ├── IPFilterGlobalFilter.java         # IP whitelist / blacklist (order -100)
│   │   ├── DynamicCustomHeaderGlobalFilter.java # Custom headers injection
│   │   ├── NacosLoadBalancerFilter.java      # lb:// Nacos discovery load balancer
│   │   └── StaticProtocolGlobalFilter.java   # static:// static instance resolver
│   ├── ratelimiter/                 # Rate limiting (Sentinel + Redis)
│   ├── plugin/                      # PluginConfigManager — shared plugin config store
│   └── route/
│       └── NacosRouteDefinitionLocator.java  # Dynamic route loader from Nacos
├── demo-service/                    # Sample Spring Boot backend (port 9000)
└── nacos/                           # Nacos server (git submodule)
```

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|------------|
| Gateway | Spring Cloud Gateway 4.1 |
| Runtime | Spring Boot 3.2, Java 17 |
| Config & Discovery | Nacos 2.4.3 |
| Rate Limiting | Sentinel, Redis |
| Admin UI | Thymeleaf, Bootstrap |
| Build | Maven |

---

## ⚠️ Production Considerations

This project is designed as a **learning / demo** showcase. For production use, consider:

- **Persistence** — route and plugin data lives only in Nacos; add a persistent storage layer for audit history
- **Security** — add authentication/authorization to the Admin Console; enable TLS on the gateway
- **Observability** — integrate Prometheus metrics, distributed tracing (Zipkin / SkyWalking)
- **High Availability** — deploy gateway in cluster mode, use Nacos cluster, Redis Cluster / Sentinel

---

## 📄 License

MIT License — see [LICENSE](LICENSE) for details.

---

<div align="center">

**Built with ❤️ by leoli**

Found this useful? Give it a ⭐ Star!

</div>
