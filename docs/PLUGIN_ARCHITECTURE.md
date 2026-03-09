# Gateway Plugin Architecture (Strategy Pattern)

## 🎯 Overview

This document describes the new plugin-based architecture for the API Gateway, using **Strategy Pattern** for extensibility and **Refresher Pattern** for dynamic configuration.

---

## 📦 Package Structure

```
com.example.gateway/
├── plugin/                          # Strategy layer
│   ├── Plugin.java                # Strategy interface
│   ├── PluginType.java            # Strategy type enumeration
│   ├── AbstractPlugin.java        # Base class with common logic
│   ├── StrategyManager.java       # Central strategy registry
│   │
│   ├── timeout/                    # Timeout strategy
│   │   └── TimeoutStrategy.java
│   ├── ratelimiter/                # Rate limiter strategy
│   │   └── RateLimiterStrategy.java
│   ├── circuitbreaker/             # Circuit breaker strategy
│   │   └── CircuitBreakerStrategy.java
│   ├── auth/                       # Authentication strategy
│   │   └── AuthStrategy.java
│   ├── ipfilter/                   # IP filter strategy
│   │   └── IPFilterStrategy.java
│   └── tracing/                    # Distributed tracing strategy
│       └── TracingStrategy.java
│
├── refresher/                       # Configuration refresh layer
│   ├── AbstractRefresher.java     # Base refresher class
│   ├── ServiceRefresher.java      # Service config refresher
│   ├── RouteRefresher.java        # Route config refresher
│   └── PluginRefresher.java       # Plugin config refresher
│
└── manager/                         # Data caching layer
    ├── ServiceManager.java        # Service data cache
    ├── RouteManager.java          # Route data cache
    └── PluginConfigManager.java   # Plugin config cache
```

---

## 🏗️ Architecture Design

### Core Concept: Three-Layer Separation

```
┌─────────────────────────────────────────┐
│  Refresher Layer (Ears)                 │
│  - Listens to Nacos config changes     │
│  - Parses and validates config         │
│  - Triggers refresh callbacks          │
└────────────┬────────────────────────────┘
             │ onConfigChange()
             ↓
┌─────────────────────────────────────────┐
│  Manager Layer (Brain)                  │
│  - Stores configuration in memory      │
│  - Provides query interfaces           │
│  - Manages lifecycle                   │
└────────────┬────────────────────────────┘
             │ refreshStrategy()
             ↓
┌─────────────────────────────────────────┐
│  Strategy Layer (Hands)                 │
│  - Executes business logic             │
│  - Applies rules to requests           │
│  - Self-managed state (enabled/disabled)│
└─────────────────────────────────────────┘
```

---

## 🔧 Strategy Pattern Implementation

### Plugin Interface

```java
public interface Plugin {
    PluginType getType();                    // Strategy type identifier
    void apply(Map<String, Object> context); // Execute strategy logic
    void refresh(Object config);            // Refresh configuration
    boolean isEnabled();                     // Check if enabled
}
```

### AbstractPlugin Base Class

Provides common functionality:
- ✅ Enable/disable state management
- ✅ Configuration map storage
- ✅ Helper method `getConfigValue()`

### Concrete Strategies

Each strategy focuses on one concern:

| Strategy | Responsibility | Key Features |
|----------|----------------|--------------|
| **TimeoutStrategy** | Request timeout control | Per-route timeout configuration |
| **RateLimiterStrategy** | Distributed rate limiting | Redis sliding window algorithm |
| **CircuitBreakerStrategy** | Circuit breaker pattern | Resilience4j integration |
| **AuthStrategy** | Authentication handling | JWT/API Key/OAuth2 support |
| **IPFilterStrategy** | IP access control | Whitelist/blacklist modes |
| **TracingStrategy** | Distributed tracing | TraceId generation & MDC logging |

---

## 🔄 StrategyManager: Central Registry

### Auto-Discovery via Spring DI

```java
@Component
public class StrategyManager {
    
   private final Map<PluginType, Plugin> strategyMap = new ConcurrentHashMap<>();
    
    @Autowired
    public StrategyManager(List<Plugin> plugins) {
        for (Plugin plugin : plugins) {
            strategyMap.put(plugin.getType(), plugin);
            log.info("Registered strategy: {} ({})", 
                plugin.getType().getDisplayName(), 
                plugin.getClass().getSimpleName());
        }
    }
    
    public void refreshStrategy(PluginType type, Object config) {
        Plugin strategy = strategyMap.get(type);
        if (strategy != null) {
            strategy.refresh(config);
        }
    }
    
    public void applyStrategies(Map<String, Object> context) {
        for (Plugin strategy : strategyMap.values()) {
            if (strategy.isEnabled()) {
                strategy.apply(context);
            }
        }
    }
}
```

### Benefits

✅ **Zero Configuration** — Spring auto-discovers all `@Component` strategies  
✅ **Open-Closed Principle** — Add new strategies without modifying existing code  
✅ **Testability** — Each strategy can be tested independently  
✅ **Clear Responsibility** — Each strategy handles one concern  

---

## 📝 Usage Example

### Adding a New Strategy

**Step 1: Create strategy class**

```java
@Component
public class CustomStrategy extends AbstractPlugin {
    
    @Override
    public PluginType getType() {
       return PluginType.CUSTOM; // Add to PluginType enum first
    }
    
    @Override
    public void apply(Map<String, Object> context) {
        if (!isEnabled()) return;
        
        // Your business logic here
        String routeId = (String) context.get("routeId");
        log.info("Custom strategy applied for route: {}", routeId);
    }
    
    @Override
    public void refresh(Object config) {
        super.refresh(config);
        // Handle custom configuration
    }
}
```

**Step 2: Configure in gateway-plugins.json**

```json
{
  "plugins": {
    "custom": [{
      "routeId": "api",
      "enabled": true,
      "someConfig": "value"
    }]
  }
}
```

**Step 3: Done!** Strategy is automatically loaded and applied.

---

## 🎯 Integration with Filters

### Old vs New Approach

#### ❌ Old Approach (Monolithic Filter)

```java
@Component
public class MyGlobalFilter implements GlobalFilter {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Everything in one method
        checkAuth();
        checkRateLimit();
        checkCircuitBreaker();
        checkTimeout();
        // ... 500 lines of mixed logic
        
       return chain.filter(exchange);
    }
}
```

**Problems:**
- ❌ Hard to maintain
- ❌ Hard to test
- ❌ Violates Single Responsibility
- ❌ Cannot disable individual features easily

#### ✅ New Approach (Strategy-Based)

```java
@Component
public class PluginGlobalFilter implements GlobalFilter {
    
   private final StrategyManager strategyManager;
    
    public PluginGlobalFilter(StrategyManager strategyManager) {
        this.strategyManager= strategyManager;
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Map<String, Object> context = buildContext(exchange);
        
        // Apply all enabled strategies
       strategyManager.applyStrategies(context);
        
        // Check results
       Boolean allowed = (Boolean) context.get("rateLimitAllowed");
        if (allowed == null || !allowed) {
           return rejectRequest(exchange, HttpStatus.TOO_MANY_REQUESTS);
        }
        
       return chain.filter(exchange);
    }
}
```

**Benefits:**
- ✅ Clean and readable
- ✅ Each strategy is independently testable
- ✅ Can enable/disable strategies via config
- ✅ Easy to add new features

---

## 🔄 Refresher Pattern (Coming Soon)

The Refresher layer listens to Nacos configuration changes and triggers updates:

```java
@Component
public class PluginRefresher extends AbstractRefresher {
    
   private final StrategyManager strategyManager;
    
    @Override
   protected void doRefresh(Object config) {
        // Parse plugin configuration
       Map<String, Object> pluginConfigs = parsePluginConfigs(config);
        
        // Refresh each strategy
       for (Map.Entry<PluginType, Object> entry : pluginConfigs.entrySet()) {
            strategyManager.refreshStrategy(entry.getKey(), entry.getValue());
        }
    }
}
```

---

## 📊 Performance Considerations

### Memory Footprint

- **Strategy Instances:** Singleton (managed by Spring)
- **Configuration Cache:** In-memory `ConcurrentHashMap`
- **Per-Request Context:** Lightweight `HashMap` (~1KB per request)

### Execution Order

Strategies are applied in order of their importance:

1. **IP Filter** (fast rejection, order: -280)
2. **Authentication** (identity verification, order: -250)
3. **Rate Limiter** (traffic control, order: -50)
4. **Circuit Breaker** (fault tolerance, order: -100)
5. **Timeout** (protect downstream, order: -200)
6. **Tracing** (observability, order: -300)

---

## 🎓 Design Principles

### 1. Single Responsibility Principle (SRP)

Each strategy handles ONE concern:
- `TimeoutStrategy` → Only timeout
- `AuthStrategy` → Only authentication
- `RateLimiterStrategy` → Only rate limiting

### 2. Open-Closed Principle (OCP)

- **Open for extension** — Add new strategies easily
- **Closed for modification** — No need to change `StrategyManager`

### 3. Dependency Injection (DI)

- Spring manages strategy lifecycle
- Zero manual registration
- Automatic dependency resolution

### 4. Separation of Concerns

- **Refresher** → Listens to config changes
- **Manager** → Stores and manages data
- **Strategy** → Executes business logic

---

## 🚀 Migration Guide

### From Old Filter to New Strategy

**Before:**
```java
@Component
public class TimeoutGlobalFilter implements GlobalFilter {
    // 200 lines of mixed logic
}
```

**After:**
```java
@Component
public class TimeoutStrategy implements Plugin {
    @Override
    public PluginType getType() { return PluginType.TIMEOUT; }
    
    @Override
    public void apply(Map<String, Object> context) {
        // Focused logic only
    }
}
```

**Migration Steps:**
1. Create new strategy class
2. Move business logic from filter to strategy
3. Update filter to delegate to `StrategyManager`
4. Test thoroughly
5. Remove old filter

---

## 📈 Future Enhancements

### Planned Features

1. **Dynamic Strategy Loading** — Load strategies from JAR files at runtime
2. **Strategy Chaining** — Define execution order dynamically
3. **Hot Reload** — Update strategy configuration without restart
4. **Metrics Collection** — Track strategy execution time and success rate
5. **Conditional Execution** — Execute strategies based on request attributes

---

## 🎯 Summary

The new plugin architecture brings:

✅ **Clean Code** — Each strategy has single responsibility  
✅ **Easy Testing** — Strategies can be tested in isolation  
✅ **High Extensibility** — Add features without modifying core code  
✅ **Dynamic Configuration** — Hot reload via Nacos + Refresher 
✅ **Production Ready** — Proven patterns (Strategy + Observer)  

This design demonstrates **professional-grade architecture thinking** suitable for enterprise systems! 🚀
