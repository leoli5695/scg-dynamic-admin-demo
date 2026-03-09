# Spring Cloud Gateway Dynamic Extension Demo

A production-grade architecture demo of **Spring Cloud Gateway (SCG)** with dynamic routing, load balancing, and hot-pluggable plugins — all managed through a web console without any YAML editing or service restarts.

> ⚠️ **Important Notice**
>
> This is a **DEMO project** with complete core functions and a production-grade architecture, suitable for learning and secondary development. **NOT recommended for direct production use** due to:
> - All configurations (routes / services / plugins) are stored **only in Nacos Config Center — no database persistence**. Data loss in Nacos results in total configuration loss.
> - Gateway Admin API has **no authentication / authorization** (fully exposed).
> - No configuration change audit log.
> - Rate limiting counters are stored in Redis (counters reset after Redis restart).

---

## 🎬 Demo Video

▶️ **[Watch on YouTube](https://youtu.be/JASijtZ5cNk)** — full walkthrough: dynamic routing, load balancing, rate limiting, IP filter, and timeout plugin in action.

---

## 📋 Module Composition

| Module | Port | Description |
|--------|------|-------------|
| `my-gateway` | 80 | Core gateway — Spring Cloud Gateway extended |
| `gateway-admin` | 8080 | Management console (REST API + Web UI) |
| `demo-service` | **9000 / 9001** | Demo backend — **start 2 instances to demonstrate load balancing** |
| Nacos | 8848 | Config center + Service registry |
| Redis | 6379 | Rate limiting counter storage |

---

## 🚀 Quick Start

### Prerequisites

- JDK 17+
- Maven 3.8+
- Nacos 2.4.3 (standalone)
- Redis 6.0+

### Step 1 — Start Infrastructure

```bash
# Nacos standalone
cd nacos/bin
startup.cmd -m standalone        # Windows
sh startup.sh -m standalone      # Linux / macOS

# Redis
redis-server
```

### Step 2 — Bootstrap Nacos Configurations

In the Nacos console (`http://localhost:8848/nacos`), create the following under **Namespace: public / Group: DEFAULT_GROUP**:

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

### Step 3 — Start Services

```bash
# demo-service instance 1 (port 9000)
cd demo-service
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=9000"

# demo-service instance 2 (new terminal, port 9001)
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=9001"

# Core gateway
cd my-gateway && mvn spring-boot:run

# Admin console
cd gateway-admin && mvn spring-boot:run
```

### Step 4 — Verify

| Component | URL |
|-----------|-----|
| Admin Console | http://localhost:8080 |
| Gateway entry | http://localhost:80 |
| Nacos Console | http://localhost:8848/nacos |

Test load balancing across two instances:
```bash
# Should alternate between port 9000 and 9001 (weight 1:2)
curl http://localhost/api/hello
```

---

## 📂 Documentation

| Document | Description |
|----------|-------------|
| [Architecture](docs/ARCHITECTURE.md) | Data flow, filter chain, real-time update mechanism |
| [Features](docs/FEATURES.md) | Detailed functional description of all modules |
| [API Reference](docs/API.md) | Management console REST API list |

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|------------|
| Gateway | Spring Cloud Gateway 4.1 |
| Runtime | Spring Boot 3.2, Java 17, WebFlux (Reactive) |
| Config & Discovery | Nacos 2.4.3 |
| Rate Limiting | Redis 6.0 (sliding window) |
| Admin UI | Thymeleaf, Bootstrap |
| Build | Maven |

---

## 📁 Project Structure

```
scg-dynamic-admin-demo/
├── gateway-admin/                        # Admin console (port 8080)
│   ├── controller/                       # REST API + Thymeleaf controllers
│   ├── model/                            # RouteDefinition, ServiceDefinition, PluginConfig …
│   └── service/                          # RouteService, ServiceManager, PluginService …
├── my-gateway/                           # SCG gateway core (port 80)
│   ├── filter/
│   │   ├── TimeoutGlobalFilter.java      # Per-route timeout (order -200)
│   │   ├── IPFilterGlobalFilter.java     # IP whitelist / blacklist (order -100)
│   │   ├── NacosLoadBalancerFilter.java  # lb:// Nacos discovery LB (order 10150)
│   │   └── StaticProtocolGlobalFilter.java  # static:// resolver (order 10001)
│   ├── ratelimiter/                      # Redis sliding-window rate limiter (order -50)
│   ├── plugin/                           # PluginConfigManager — shared plugin config store
│   └── route/
│       └── NacosRouteDefinitionLocator.java  # Dynamic route loader + RefreshRoutesEvent
├── demo-service/                         # Sample Spring Boot backend (port 9000 / 9001)
└── docs/                                 # Architecture, Features, API reference
```

---

<div align="center">

**Built with ❤️ by leoli**

Found this useful? Give it a ⭐ Star!

</div>
