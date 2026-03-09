# Plugin Architecture Quick Start

## 🚀 Overview

This guide shows you how to use the new plugin-based architecture in your gateway.

---

## 📦 Architecture Components

```
┌─────────────────────────────────────────┐
│  Nacos Config (gateway-plugins.json)    │
│  - Stores all plugin configurations    │
│  - Hot reload supported (< 1s)         │
└────────────┬────────────────────────────┘
             │ Push notification
             ↓
┌─────────────────────────────────────────┐
│  NacosConfigListener                   │
│  - Listens to config changes           │
│  - Triggers refresh on update          │
└────────────┬────────────────────────────┘
             │ onConfigChange()
             ↓
┌─────────────────────────────────────────┐
│  PluginRefresher                       │
│  - Parses JSON config                  │
│  - Merges configs per strategy type    │
│  - Calls StrategyManager.refresh()     │
└────────────┬────────────────────────────┘
             │ refreshStrategy()
             ↓
┌─────────────────────────────────────────┐
│  StrategyManager                       │
│  - Central registry for all strategies │
│  - Auto-discovers via Spring DI        │
│  - Routes config to correct strategy   │
└────────────┬────────────────────────────┘
             │ applyStrategies()
             ↓
┌─────────────────────────────────────────┐
│  PluginGlobalFilter (-200)              │
│  - Builds request context              │
│  - Applies all enabled strategies      │
│  - Checks results & rejects if needed  │
└────────────┬────────────────────────────┘
             │
             ↓
┌─────────────────────────────────────────┐
│  Six Core Strategies                    │
│  ✓ IP Filter (whitelist/blacklist)     │
│  ✓ Auth (JWT/API Key/OAuth2)           │
│  ✓ Rate Limiter (Redis sliding window) │
│  ✓ Circuit Breaker (Resilience4j)      │
│  ✓ Timeout (per-route timeout)         │
│  ✓ Tracing (TraceId generation)        │
└─────────────────────────────────────────┘
```

---

## 🔧 Configuration Example

### gateway-plugins.json

Create this file in Nacos with data-id: `gateway-plugins.json`

```json
{
  "ipFilters": [
    {
      "routeId": "api",
      "enabled": true,
      "mode": "blacklist",
      "blacklist": ["192.168.1.100", "10.0.0.50"],
      "whitelist": []
    }
  ],
  
  "authConfigs": [
    {
      "routeId": "api",
      "enabled": true,
      "authType": "JWT",
      "secretKey": "your-secret-key-here",
      "expirationMinutes": 30
    }
  ],
  
  "rateLimiters": [
    {
      "routeId": "api",
      "enabled": true,
      "defaultQps": 100,
      "windowSize": 60
    }
  ],
  
  "circuitBreakers": [
    {
      "routeId": "api",
      "enabled": true,
      "failureRateThreshold": 50,
      "waitDurationInOpenState": 30,
      "slidingWindowSize": 10,
      "minimumNumberOfCalls": 10
    }
  ],
  
  "timeouts": [
    {
      "routeId": "api",
      "enabled": true,
      "defaultTimeout": 3000
    }
  ],
  
  "tracing": [
    {
      "routeId": "api",
      "enabled": true
    }
  ]
}
```

---

## 📝 Step-by-Step Usage

### Step 1: Deploy Gateway

```bash
# Build the project
mvn clean package

# Run my-gateway
cd my-gateway
java -jar target/my-gateway-0.0.1-SNAPSHOT.jar \
  --spring.cloud.nacos.config.server-addr=localhost:8848 \
  --nacos.config.data-id=gateway-plugins.json
```

---

### Step 2: Configure Nacos

1. Open Nacos Console (http://localhost:8848/nacos)
2. Navigate to **Configuration Management** → **Configuration List**
3. Click **+** to add new configuration
4. Fill in:
   - **Data ID**: `gateway-plugins.json`
   - **Group**: `DEFAULT_GROUP`
   - **Format**: `JSON`
5. Paste the JSON configuration from above
6. Click **Publish**

**Result:**Gateway automatically loads the configuration within 1 second!

---

### Step 3: Test Each Strategy

#### Test IP Filter

```bash
# Normal request (should pass)
curl -v http://localhost/api/users

# Request from blocked IP (will be rejected)
# Simulate by configuring blacklist IP
curl -v http://localhost/api/users
# Response: HTTP 403 Forbidden
# Header: X-Reject-Reason: IP address blocked
```

---

#### Test Authentication

```bash
# Without token (rejected)
curl -v http://localhost/api/users
# Response: HTTP 401 Unauthorized
# Header: X-Reject-Reason: Authentication failed

# With valid JWT token (allowed)
TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
curl -v http://localhost/api/users \
  -H "Authorization: Bearer $TOKEN"
# Response: HTTP 200 OK
```

---

#### Test Rate Limiter

```bash
# Send 150 requests quickly (limit is 100 QPS)
for i in {1..150}; do
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost/api/users
done

# First 100 requests: HTTP 200
# Remaining 50 requests: HTTP 429 Too Many Requests
```

---

#### Test Circuit Breaker

```bash
# Simulate downstream failures (e.g., make demo-service return 500)
# After 50% failure rate (5 out of 10 calls fail):

curl -v http://localhost/api/users
# Response: HTTP 503 Service Unavailable
# Header: X-Reject-Reason: Circuit breaker is open
```

---

#### Test Distributed Tracing

```bash
# Any request will have trace ID
curl -v http://localhost/api/users

# Check response headers
< HTTP/1.1 200 OK
< X-Trace-Id: a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6

# Trace ID also appears in logs
2024-03-10 12:34:56 [a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6] INFO ...
```

---

## 🎯 Customizing Strategies

### Enable/Disable Specific Strategy

Edit `gateway-plugins.json` in Nacos:

```json
{
  "ipFilters": [
    {
      "routeId": "api",
      "enabled": false  // ← Disable IP filter
    }
  ]
}
```

**Result:**IP filter disabled immediately without restart!

---

### Update Rate Limit QPS

```json
{
  "rateLimiters": [
    {
      "routeId": "api",
      "enabled": true,
      "defaultQps": 200  // ← Increase from 100 to 200
    }
  ]
}
```

**Result:** New QPS limit applied within 1 second!

---

### Add Multiple Configurations for Same Strategy

```json
{
  "ipFilters": [
    {
      "routeId": "api",
      "enabled": true,
      "mode": "blacklist",
      "blacklist": ["192.168.1.100"]
    },
    {
      "routeId": "admin",
      "enabled": true,
      "mode": "whitelist",
      "whitelist": ["10.0.0.1", "10.0.0.2"]
    }
  ]
}
```

**Result:** Different routes have different IP filter rules!

---

## 🔨 Extending with Custom Strategy

### Create Your Own Strategy

**Step 1: Add new type to PluginType.java**

```java
public enum PluginType {
    RATE_LIMITER("rateLimiters", "Rate Limiter"),
    AUTH("authConfigs", "Authentication"),
    // ... existing types ...
    
    CUSTOM("customConfigs", "Custom Strategy"); // ← Add this
}
```

---

**Step 2: Create custom strategy class**

```java
package com.example.gateway.plugin.custom;

import com.example.gateway.plugin.AbstractPlugin;
import com.example.gateway.plugin.PluginType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class CustomStrategy extends AbstractPlugin {
    
    @Override
    public PluginType getType() {
      return PluginType.CUSTOM;
    }
    
    @Override
    public void apply(Map<String, Object> context) {
        if (!isEnabled()) {
            log.trace("Custom strategy disabled, skipping");
         return;
        }
        
        String routeId = (String) context.get("routeId");
        String clientIp = (String) context.get("clientIp");
        
        // Your custom logic here
        log.info("Custom strategy applied for route={}, ip={}", routeId, clientIp);
        
        // Example: Add custom header
       context.put("customHeader", "my-custom-value");
    }
    
    @Override
    public void refresh(Object config) {
        super.refresh(config);
        // Handle custom configuration
        log.info("Custom strategy refreshed");
    }
}
```

---

**Step 3: Configure in gateway-plugins.json**

```json
{
  "customConfigs": [
    {
      "routeId": "api",
      "enabled": true,
      "someCustomParam": "value",
      "anotherParam": 123
    }
  ]
}
```

**Done!** Your custom strategy is automatically loaded and executed! ✨

---

## 📊 Monitoring & Debugging

### Check Active Strategies

```bash
# View application logs
tail -f my-gateway/logs/app.log | grep "Registered strategy"

# Example output:
# Registered strategy: Rate Limiter (RateLimiterStrategy)
# Registered strategy: Authentication (AuthStrategy)
# Registered strategy: Circuit Breaker (CircuitBreakerStrategy)
# ...
# Total 6 strategies registered
```

---

### Monitor Config Changes

```bash
# Watch config refresh logs
tail -f my-gateway/logs/app.log | grep "Refreshed strategy"

# Example output:
# Refreshed strategy Rate Limiter with2 configs
# Refreshed strategy Authentication with 1 configs
# Refreshed strategy Circuit Breaker with 1 configs
```

---

### Debug Request Processing

Enable debug logging in `application.yml`:

```yaml
logging:
  level:
    com.example.gateway.plugin: DEBUG
    com.example.gateway.filter: DEBUG
```

**Example debug output:**
```
DEBUG: IP filter check: ip=192.168.1.50, allowed=true
DEBUG: Auth applied: route=api, type=JWT
DEBUG: Rate limiter check: route=api, allowed=true
DEBUG: Circuit breaker check: route=api, state=CLOSED
DEBUG: Applied timeout 3000ms for route api
DEBUG: Tracing applied: traceId=a1b2c3d4e5f6g7h8
DEBUG: All strategies applied successfully, continuing filter chain
```

---

## 🎯 Best Practices

### 1. Configuration Organization

✅ **DO:** Group configs by route for clarity
```json
{
  "ipFilters": [
    {"routeId": "api", ...},
    {"routeId": "admin", ...}
  ]
}
```

❌ **DON'T:** Mix routes randomly

---

### 2. Enable/Disable During Development

Use `enabled: false` instead of removing configs:
```json
{
  "authConfigs": [{
    "routeId": "api",
    "enabled": false  // ← Temporarily disable for testing
  }]
}
```

---

### 3. Gradual Rollout

Start with conservative limits, then increase:
```json
{
  "rateLimiters": [{
    "routeId": "api",
    "defaultQps": 50    // ← Start low
  }]
}

// After monitoring, update to:
{
  "rateLimiters": [{
    "routeId": "api",
    "defaultQps": 200   // ← Increase based on metrics
  }]
}
```

---

### 4. Circuit Breaker Tuning

Adjust based on your service characteristics:
```json
{
  "circuitBreakers": [{
    "failureRateThreshold": 50,      // ← 50% failures trigger open
    "minimumNumberOfCalls": 10,      // ← Wait for 10 calls before calculating
    "slidingWindowSize": 10,         // ← Consider last 10 calls
    "waitDurationInOpenState": 30    // ← Wait 30s before trying again
  }]
}
```

---

## 🚨 Troubleshooting

### Issue: Strategies Not Loading

**Check:**
1. ✅ Nacos server is running (`http://localhost:8848`)
2. ✅ `gateway-plugins.json` exists in Nacos
3. ✅ JSON format is valid (use JSONLint to validate)
4. ✅ Application logs show "Loaded initial plugin config from Nacos"

---

### Issue: Config Changes Not Applied

**Check:**
1. ✅ Published the config in Nacos (not just saved)
2. ✅ Logs show "Received config change from Nacos"
3. ✅ Logs show "Refreshed strategy XXX with Y configs"
4. ✅ No JSON parsing errors in logs

---

### Issue: Strategy Not Working

**Debug Steps:**
1. Enable DEBUG logging
2. Check if strategy is enabled in config
3. Verify routeId matches your request
4. Look for "Strategy XXX applied" in logs
5. Check rejection reason in response headers

---

## 📈 Performance Tips

### Optimize Strategy Execution Order

Current order: `-200` (between TraceId and RateLimiter)

If you need custom ordering, adjust in `PluginGlobalFilter.java`:
```java
@Override
public int getOrder() {
 return -200; // Change this value
}
```

**Recommended orders:**
- TraceId: `-300` (first)
- IP Filter: `-280` (fast rejection)
- Auth: `-250` (identity verification)
- **PluginGlobalFilter: `-200`** (applies all strategies)
- Timeout: `-200` (protect downstream)
- Circuit Breaker: `-100` (prevent cascade)
- Rate Limiter: `-50` (last defense)

---

## 🎓 Summary

The plugin architecture provides:

✅ **Zero Downtime Configuration** — Hot reload via Nacos  
✅ **Easy Extension** — Add strategies in 3 steps  
✅ **Clean Separation** — Each strategy handles one concern  
✅ **Production Ready** — Built-in error handling and logging  
✅ **Flexible Control** — Enable/disable per route  

**Start building your custom gateway now!** 🚀
