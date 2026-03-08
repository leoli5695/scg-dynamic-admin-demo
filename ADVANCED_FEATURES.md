# Advanced Features Implementation Guide

## A. Distributed Rate Limiting (Redis-based) - **CORE FEATURE**

### Why It's Critical
Distributed rate limiting is essential for API gateways to:
- Prevent DDoS attacks
- Protect backend services from overload
- Enforce API quotas per client/tenant
- Ensure fair resource allocation

### Architecture

```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │
       ▼
┌─────────────────────────┐
│     My-Gateway          │
│  ─────────────────────  │
│  RateLimiterFilter      │
│         │               │
│         ▼               │
│  ─────────────────────  │
│  Redis Rate Limiter     │
│  (Token Bucket)         │
└───────────┬─────────────┘
            │
            ▼
     ┌─────────────┐
     │    Redis    │
     │  (Cluster)  │
     └─────────────┘
```

### Implementation Code

#### 1. Data Model (RateLimiterConfig.java)
```java
@Data
public class RateLimiterConfig {
    private String routeId;
    private boolean enabled;
    private int qps;              // Queries per second
    private int burstCapacity;    // Max burst requests
    private long timeoutMs;       // Timeout in milliseconds
}
```

#### 2. Redis Rate Limiter Utility (RedisRateLimiter.java)
```java
@Component
@Slf4j
public class RedisRateLimiter {
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    private static final String KEY_PREFIX = "rate_limiter:";
    
    /**
     * Try to acquire a token using sliding window algorithm
     */
    public boolean tryAcquire(String routeId, int qps, int burstCapacity) {
        String key = KEY_PREFIX + routeId;
        
        return redisTemplate.execute((RedisCallback<Boolean>) connection -> {
            long now = System.currentTimeMillis();
            long windowSize = 1000; // 1 second window
            
            byte[] keyBytes = key.getBytes();
            
            // Remove expired entries
            connection.zSetCommands().zRemRangeByScore(keyBytes, 0, now - windowSize);
            
            // Count current requests in window
            Long count = connection.zSetCommands().zCard(keyBytes);
            
            if (count != null && count < qps) {
                // Add current request
                connection.zSetCommands().zAdd(keyBytes, now, String.valueOf(now).getBytes());
                connection.expire(keyBytes, 2); // Expire after 2 seconds
                return true;
            }
            
            return false;
        });
    }
    
    /**
     * Token bucket algorithm implementation
     */
    public boolean tryAcquireWithTokenBucket(String routeId, int qps, int burstCapacity) {
        String key = KEY_PREFIX + "token:" + routeId;
        
        return redisTemplate.execute((RedisCallback<Boolean>) connection -> {
            byte[] keyBytes = key.getBytes();
            
            // Get current tokens and last refill time
            Double tokens = connection.stringCommands().getDouble(keyBytes);
            Long lastRefill = connection.stringCommands().getLong((key + ":time").getBytes());
            
            if (tokens == null) {
                // Initialize bucket
                tokens = (double) burstCapacity;
                lastRefill = System.currentTimeMillis();
            }
            
            // Calculate tokens to add based on time elapsed
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefill;
            double tokensToAdd = (elapsed / 1000.0) * qps;
            
            tokens = Math.min(burstCapacity, tokens + tokensToAdd);
            
            if (tokens >= 1.0) {
                // Consume one token
                tokens -= 1.0;
                connection.stringCommands().set(keyBytes, tokens.toString().getBytes());
                connection.stringCommands().set((key + ":time").getBytes(), String.valueOf(now).getBytes());
                return true;
            }
            
            // Update last refill time
            connection.stringCommands().set((key + ":time").getBytes(), String.valueOf(now).getBytes());
            return false;
        });
    }
}
```

#### 3. Rate Limiter Global Filter (RateLimiterGlobalFilter.java)
```java
@Component
@Slf4j
public class RateLimiterGlobalFilter implements GlobalFilter, Ordered {
    
    @Autowired
    private PluginConfigManager pluginConfigManager;
    
    @Autowired
    private RedisRateLimiter redisRateLimiter;
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String routeId = getRouteId(exchange);
        
        // Check if rate limiter is configured for this route
        if (!pluginConfigManager.hasRateLimiter(routeId)) {
            return chain.filter(exchange);
        }
        
        RateLimiterConfig config = pluginConfigManager.getRateLimiterConfig(routeId);
        
        if (!config.isEnabled()) {
            return chain.filter(exchange);
        }
        
        // Extract client identifier (IP, API key, etc.)
        String clientId = extractClientId(exchange.getRequest());
        String rateLimitKey = routeId + ":" + clientId;
        
        // Try to acquire permission from Redis
        boolean allowed = redisRateLimiter.tryAcquireWithTokenBucket(
            rateLimitKey, 
            config.getQps(), 
            config.getBurstCapacity()
        );
        
        if (allowed) {
            log.debug("Rate limit allowed for route: {}, client: {}", routeId, clientId);
            return chain.filter(exchange);
        } else {
            log.warn("Rate limit exceeded for route: {}, client: {}", routeId, clientId);
            
            // Return 429 Too Many Requests
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().add("X-RateLimit-Limit", String.valueOf(config.getQps()));
            exchange.getResponse().getHeaders().add("X-RateLimit-Remaining", "0");
            exchange.getResponse().getHeaders().add("Retry-After", "1");
            
            return exchange.getResponse().setComplete();
        }
    }
    
    private String extractClientId(ServerHttpRequest request) {
        // Priority: API Key > Client IP > Anonymous
        String apiKey = request.getHeaders().getFirst("X-API-Key");
        if (apiKey != null) {
            return "api_key:" + apiKey;
        }
        
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null) {
            return "ip:" + remoteAddress.getAddress().getHostAddress();
        }
        
        return "anonymous";
    }
    
    @Override
    public int getOrder() {
        return 5000; // Execute early in the filter chain
    }
}
```

#### 4. REST API Controller (PluginController.java)
```java
@RestController
@RequestMapping("/api/plugins")
public class PluginController {
    
    @Autowired
    private NacosConfigManager nacosConfigManager;
    
    @PostMapping("/rate-limiter")
    public ResponseEntity<?> configureRateLimiter(@RequestBody RateLimiterConfig config) {
        try {
            // Get current plugins config
            PluginConfig pluginConfig = nacosConfigManager.getPluginsConfig("gateway-plugins.json");
            
            // Update rate limiter config
            List<RateLimiterConfig> rateLimiters = pluginConfig.getRateLimiters();
            rateLimiters.removeIf(rl -> rl.getRouteId().equals(config.getRouteId()));
            rateLimiters.add(config);
            
            // Push to Nacos
            String json = objectMapper.writeValueAsString(pluginConfig);
            boolean success = nacosConfigManager.publishConfig("gateway-plugins.json", json);
            
            if (success) {
                return ResponseEntity.ok(Map.of(
                    "message", "Rate limiter configured successfully",
                    "routeId", config.getRouteId()
                ));
            }
            
            return ResponseEntity.status(500).body(Map.of(
                "message", "Failed to publish configuration"
            ));
        } catch (Exception e) {
            log.error("Error configuring rate limiter", e);
            return ResponseEntity.status(500).body(Map.of(
                "message", "Internal server error: " + e.getMessage()
            ));
        }
    }
    
    @GetMapping("/rate-limiters")
    public ResponseEntity<?> getRateLimiters() {
        try {
            PluginConfig pluginConfig = nacosConfigManager.getPluginsConfig("gateway-plugins.json");
            return ResponseEntity.ok(Map.of(
                "data", pluginConfig.getRateLimiters()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "message", "Failed to load rate limiters"
            ));
        }
    }
}
```

---

## B. IP Whitelist/Blacklist - **SECURITY FEATURE**

### Purpose
Control access based on client IP addresses:
- **Whitelist**: Only allow specified IPs
- **Blacklist**: Block specified IPs

### Implementation Code

#### IP Access Control Filter (IpAccessControlFilter.java)
```java
@Component
@Slf4j
public class IpAccessControlFilter implements GlobalFilter, Ordered {
    
    @Autowired
    private PluginConfigManager pluginConfigManager;
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String clientIp = extractClientIp(request);
        String routeId = getRouteId(exchange);
        
        // Check if IP access control is enabled for this route
        IpAccessConfig ipConfig = pluginConfigManager.getIpAccessConfig(routeId);
        
        if (ipConfig == null || !ipConfig.isEnabled()) {
            return chain.filter(exchange);
        }
        
        // Check blacklist first
        if (ipConfig.getBlacklist() != null && !ipConfig.getBlacklist().isEmpty()) {
            if (matchesIpPattern(clientIp, ipConfig.getBlacklist())) {
                log.warn("IP {} blocked by blacklist for route {}", clientIp, routeId);
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }
        }
        
        // Check whitelist (if configured, only whitelisted IPs are allowed)
        if (ipConfig.getWhitelist() != null && !ipConfig.getWhitelist().isEmpty()) {
            if (!matchesIpPattern(clientIp, ipConfig.getWhitelist())) {
                log.warn("IP {} not in whitelist for route {}", clientIp, routeId);
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }
        }
        
        log.debug("IP {} allowed for route {}", clientIp, routeId);
        return chain.filter(exchange);
    }
    
    private String extractClientIp(ServerHttpRequest request) {
        // Check X-Forwarded-For header first
        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            return forwardedFor.split(",")[0].trim();
        }
        
        // Check X-Real-IP header
        String realIp = request.getHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp;
        }
        
        // Fall back to remote address
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        
        return "unknown";
    }
    
    private boolean matchesIpPattern(String ip, List<String> patterns) {
        for (String pattern : patterns) {
            if (pattern.contains("*")) {
                // Wildcard matching: 192.168.*.*
                String regex = pattern.replace(".", "\\.").replace("*", "\\d{1,3}");
                if (ip.matches(regex)) {
                    return true;
                }
            } else if (pattern.equals(ip)) {
                return true;
            } else if (pattern.contains("/")) {
                // CIDR notation: 192.168.1.0/24
                if (matchesCidr(ip, pattern)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private boolean matchesCidr(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            String network = parts[0];
            int prefixLength = Integer.parseInt(parts[1]);
            
            byte[] ipBytes = InetAddress.getByName(ip).getAddress();
            byte[] networkBytes = InetAddress.getByName(network).getAddress();
            
            long ipNum = bytesToLong(ipBytes);
            long networkNum = bytesToLong(networkBytes);
            long mask = -1L << (32 - prefixLength);
            
            return (ipNum & mask) == (networkNum & mask);
        } catch (Exception e) {
            log.error("Invalid CIDR notation: {}", cidr, e);
            return false;
        }
    }
    
    private long bytesToLong(byte[] bytes) {
        long result = 0;
        for (byte b : bytes) {
            result = (result << 8) | (b & 0xFF);
        }
        return result;
    }
    
    @Override
    public int getOrder() {
        return 4000; // Execute before rate limiting
    }
}
```

### Configuration Example (gateway-plugins.json)
```json
{
  "plugins": {
    "ipAccess": [
      {
        "routeId": "admin-route",
        "enabled": true,
        "whitelist": [
          "192.168.1.0/24",
          "10.0.0.*",
          "172.16.0.100"
        ]
      },
      {
        "routeId": "api-route",
        "enabled": true,
        "blacklist": [
          "203.0.113.0/24",
          "198.51.100.50"
        ]
      }
    ]
  }
}
```

---

## C. CORS Configuration - **FRONTEND INTEGRATION**

### Purpose
Enable cross-origin requests from web applications.

### Implementation Code

#### CORS Global Filter (CorsGlobalFilter.java)
```java
@Component
@Slf4j
public class CorsGlobalFilter implements GlobalFilter, Ordered {
    
    @Autowired
    private PluginConfigManager pluginConfigManager;
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        
        // Handle preflight OPTIONS request
        if (HttpMethod.OPTIONS.matches(request.getMethod().value())) {
            log.debug("Handling CORS preflight request for {}", request.getURI());
            addCorsHeaders(response, exchange);
            return Mono.empty();
        }
        
        // Add CORS headers to response
        addCorsHeaders(response, exchange);
        
        return chain.filter(exchange);
    }
    
    private void addCorsHeaders(ServerHttpResponse response, ServerWebExchange exchange) {
        String routeId = getRouteId(exchange);
        CorsConfig corsConfig = pluginConfigManager.getCorsConfig(routeId);
        
        HttpHeaders headers = response.getHeaders();
        
        if (corsConfig != null && corsConfig.isEnabled()) {
            // Allow specific origins or all (*)
            headers.add("Access-Control-Allow-Origin", corsConfig.getAllowedOrigins());
            
            // Allowed methods
            String allowedMethods = String.join(", ", corsConfig.getAllowedMethods());
            headers.add("Access-Control-Allow-Methods", allowedMethods);
            
            // Allowed headers
            if (corsConfig.getAllowedHeaders() != null && !corsConfig.getAllowedHeaders().isEmpty()) {
                String allowedHeaders = String.join(", ", corsConfig.getAllowedHeaders());
                headers.add("Access-Control-Allow-Headers", allowedHeaders);
            }
            
            // Allow credentials
            if (corsConfig.isAllowCredentials()) {
                headers.add("Access-Control-Allow-Credentials", "true");
            }
            
            // Max age
            headers.add("Access-Control-Max-Age", String.valueOf(corsConfig.getMaxAge()));
        } else {
            // Default CORS settings
            headers.add("Access-Control-Allow-Origin", "*");
            headers.add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            headers.add("Access-Control-Allow-Headers", "*");
            headers.add("Access-Control-Max-Age", "3600");
        }
    }
    
    @Override
    public int getOrder() {
        return -100; // Execute early
    }
}
```

---

## D. Design Patterns for Future Enhancements

### 1. Timeout Configuration - **DESIGN CONCEPT ONLY**

#### Problem Statement
Backend services may become slow or unresponsive. The gateway should enforce timeouts.

#### Core Code Example
```java
@Component
public class TimeoutGlobalFilter implements GlobalFilter, Ordered {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String routeId = getRouteId(exchange);
        Duration timeout = getTimeoutForRoute(routeId); // Get from config
        
        return chain.filter(exchange)
            .timeout(timeout, Mono.defer(() -> {
                log.warn("Request timeout for route: {}", routeId);
                exchange.getResponse().setStatusCode(HttpStatus.GATEWAY_TIMEOUT);
                
                Map<String, Object> error = Map.of(
                    "error", "Gateway Timeout",
                    "message", "The upstream service took too long",
                    "routeId", routeId
                );
                
                return exchange.getResponse().writeWith(Mono.just(
                    exchange.getResponse().bufferFactory().wrap(
                        JacksonUtils.toJson(error).getBytes()
                    )
                ));
            }));
    }
    
    private Duration getTimeoutForRoute(String routeId) {
        // Query from Nacos configuration
        // Default: 30 seconds
        return Duration.ofSeconds(30);
    }
    
    @Override
    public int getOrder() {
        return 1000;
    }
}
```

---

### 2. Authentication & Authorization - **DESIGN CONCEPT ONLY**

#### Problem Statement
Validate requests before forwarding to backend services.

#### Core Code Example (JWT Validation)
```java
@Component
@Slf4j
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {
    
    @Autowired
    private JwtDecoder jwtDecoder;
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String routeId = getRouteId(exchange);
        
        // Skip authentication for public routes
        if (isPublicRoute(routeId)) {
            return chain.filter(exchange);
        }
        
        // Extract JWT from Authorization header
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "Missing Authorization header");
        }
        
        String token = authHeader.substring(7);
        
        try {
            // Validate JWT
            Jwt jwt = jwtDecoder.decode(token);
            
            if (jwt.isExpired()) {
                return unauthorized(exchange, "Token expired");
            }
            
            // Add user info to request headers
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-User-Id", jwt.getSubject())
                .header("X-User-Roles", String.join(",", jwt.getClaimAsStringList("roles")))
                .build();
            
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
            
        } catch (JwtException e) {
            return unauthorized(exchange, "Invalid token: " + e.getMessage());
        }
    }
    
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        
        Map<String, Object> error = Map.of(
            "error", "Unauthorized",
            "message", message
        );
        
        return response.writeWith(Mono.just(
            response.bufferFactory().wrap(JacksonUtils.toJson(error).getBytes())
        ));
    }
    
    @Override
    public int getOrder() {
        return 3000; // Before routing
    }
}
```

---

### 3. Circuit Breaker Pattern - **DESIGN CONCEPT ONLY**

#### Problem Statement
When backend services fail repeatedly, stop sending requests temporarily.

#### Core Code Example (Resilience4j)
```java
@Component
public class CircuitBreakerFilter implements GlobalFilter, Ordered {
    
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String serviceId = extractServiceId(exchange);
        CircuitBreaker cb = getCircuitBreaker(serviceId);
        
        return Mono.fromSupplier(() -> chain.filter(exchange))
            .transformDeferred(CircuitBreakerOperator.of(cb))
            .onErrorResume(CallNotPermittedException.class, ex -> {
                log.warn("Circuit breaker OPEN for service: {}", serviceId);
                exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                
                Map<String, Object> error = Map.of(
                    "error", "Service Unavailable",
                    "message", "Circuit breaker is OPEN",
                    "serviceId", serviceId
                );
                
                return exchange.getResponse().writeWith(Mono.just(
                    exchange.getResponse().bufferFactory().wrap(
                        JacksonUtils.toJson(error).getBytes()
                    )
                ));
            });
    }
    
    private CircuitBreaker getCircuitBreaker(String serviceId) {
        return circuitBreakers.computeIfAbsent(serviceId, id -> {
            CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)  // Open if 50% failures
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .build();
            
            return CircuitBreaker.of(id, config);
        });
    }
    
    @Override
    public int getOrder() {
        return 2000;
    }
}
```

---

## Summary

### Implemented Features (Complete Code)
✅ **Distributed Rate Limiting** - Redis-based token bucket/sliding window  
✅ **IP Whitelist/Blacklist** - CIDR and wildcard pattern matching  
✅ **CORS Configuration** - Cross-origin request support  

### Design Concepts (Core Code Examples Only)
📝 **Timeout Configuration** - Reactor timeout with custom error responses  
📝 **Authentication (JWT)** - Token validation and user context propagation  
📝 **Circuit Breaker** - Resilience4j integration for fault tolerance  

### Next Steps for Production
1. Implement complete UI forms in gateway-admin for all features
2. Add comprehensive unit and integration tests
3. Configure monitoring and alerting
4. Performance testing and optimization
5. Documentation and runbooks

For complete implementation guidance, refer to the main [INTEGRATION_GUIDE.md](INTEGRATION_GUIDE.md).
