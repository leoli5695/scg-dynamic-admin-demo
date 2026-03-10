# 配置中心使用指南

## 📋 配置项说明

### 核心配置

| 配置项 | 默认值 | 环境变量 | 说明 |
|--------|--------|---------|------|
| `gateway.center.type` | `nacos` | `GATEWAY_CENTER_TYPE` | 切换配置中心类型：`nacos` 或 `consul` |

---

## 🔧 Nacos 配置

### application.yml（Nacos 模式）

```yaml
spring:
  cloud:
    nacos:
    config:
        server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
       namespace: ${NACOS_NAMESPACE:""}
    discovery:
        server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
       namespace: ${NACOS_NAMESPACE:""}

gateway:
  center:
  type: ${GATEWAY_CENTER_TYPE:nacos}
```

### 环境变量列表

| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| `NACOS_SERVER_ADDR` | `127.0.0.1:8848` | Nacos 服务器地址 |
| `NACOS_NAMESPACE` | `""` | 命名空间 ID（空则使用默认） |
| `NACOS_FILE_EXTENSION` | `yaml` | 配置文件扩展名 |

---

## 🔧 Consul 配置

### application.yml（Consul 模式）

```yaml
spring:
  cloud:
 consul:
     host: ${CONSUL_HOST:localhost}
      port: ${CONSUL_PORT:8500}
  config:
        prefix: ${CONSUL_CONFIG_PREFIX:config}
  discovery:
      enabled: ${CONSUL_DISCOVERY_ENABLED:true}
    health-check-path: ${CONSUL_HEALTH_CHECK_PATH:/actuator/health}
    health-check-interval: ${CONSUL_HEALTH_CHECK_INTERVAL:10s}

gateway:
  center:
  type: ${GATEWAY_CENTER_TYPE:consul}
```

### 环境变量列表

| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| `CONSUL_HOST` | `localhost` | Consul 服务器主机名/IP |
| `CONSUL_PORT` | `8500` | Consul HTTP API 端口 |
| `CONSUL_CONFIG_PREFIX` | `config` | 配置前缀 |
| `CONSUL_HEALTH_CHECK_PATH` | `/actuator/health` | 健康检查路径 |
| `CONSUL_HEALTH_CHECK_INTERVAL` | `10s` | 健康检查间隔 |

---

## 🐳 Kubernetes 部署示例

### Deployment YAML

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-gateway
spec:
 replicas: 3
  selector:
    matchLabels:
      app: my-gateway
  template:
    metadata:
      labels:
        app: my-gateway
    spec:
     containers:
      - name: my-gateway
       image: my-gateway:latest
        ports:
        - containerPort: 80
        env:
        # 使用 Nacos
        - name: GATEWAY_CENTER_TYPE
          value: "nacos"
        - name: NACOS_SERVER_ADDR
          value: "nacos.nacos.svc.cluster.local:8848"
        - name: NACOS_NAMESPACE
          value: "production"
        
        # Redis 配置
        - name: REDIS_HOST
          value: "redis.redis.svc.cluster.local"
        - name: REDIS_PORT
          value: "6379"
        
        # 日志级别
        - name: LOG_LEVEL_GATEWAY
          value: "INFO"
        - name: LOG_LEVEL_NACOS
          value: "WARN"
        
       resources:
          limits:
            memory: "512Mi"
            cpu: "500m"
         requests:
            memory: "256Mi"
            cpu: "250m"
```

### ConfigMap 方式

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: gateway-config
data:
  GATEWAY_CENTER_TYPE: "nacos"
  NACOS_SERVER_ADDR: "nacos.nacos.svc.cluster.local:8848"
  NACOS_NAMESPACE: "production"
  REDIS_HOST: "redis.redis.svc.cluster.local"
  REDIS_PORT: "6379"
  LOG_LEVEL_GATEWAY: "INFO"
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-gateway
spec:
  template:
    spec:
     containers:
      - name: my-gateway
       image: my-gateway:latest
        envFrom:
        - configMapRef:
            name: gateway-config
```

### Secret 方式（敏感信息）

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: gateway-secrets
type: Opaque
stringData:
  REDIS_PASSWORD: "my-secret-password"
  NACOS_USERNAME: "nacos"
  NACOS_PASSWORD: "nacos"
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-gateway
spec:
  template:
    spec:
     containers:
      - name: my-gateway
       image: my-gateway:latest
        env:
        - name: REDIS_PASSWORD
          valueFrom:
            secretKeyRef:
              name: gateway-secrets
             key: REDIS_PASSWORD
```

---

## 🔄 切换配置中心

### 开发环境（本地）

```bash
# 使用默认配置（Nacos）
java -jar my-gateway.jar

# 或使用环境变量
export GATEWAY_CENTER_TYPE=consul
java -jar my-gateway.jar
```

### 生产环境（Kubernetes）

**使用 Nacos：**
```yaml
env:
- name: GATEWAY_CENTER_TYPE
  value: "nacos"
- name: NACOS_SERVER_ADDR
  value: "nacos.prod.svc.cluster.local:8848"
```

**使用 Consul：**
```yaml
env:
- name: GATEWAY_CENTER_TYPE
  value: "consul"
- name: CONSUL_HOST
  value: "consul.prod.svc.cluster.local"
- name: CONSUL_PORT
  value: "8500"
```

---

## ⚠️ 注意事项

1. **环境变量优先级** - 环境变量会覆盖 YAML 中的默认值
2. **命名约定** - 环境变量使用大写，下划线分隔
3. **特殊字符处理** - YAML 中使用引号包裹包含特殊字符的值
4. **K8s Service 名称** - 使用 K8s 内部 DNS 名称访问服务

---

## 🔍 验证配置

### 查看生效的环境变量

```bash
# 在容器内执行
env | grep GATEWAY
env | grep NACOS
env | grep CONSUL
```

### 查看 Spring Boot 配置

```bash
# 访问 actuator 端点
curl http://localhost:8080/actuator/env | jq '.propertySources'
```

### 测试连接

```bash
# 如果配置正确，应该能看到相应的初始化日志
# Nacos:
NacosConfigService initialized
NacosDiscoveryService initialized

# Consul:
ConsulConfigService initialized with prefix: config
ConsulDiscoveryService initialized with Spring Cloud DiscoveryClient
```

---

## 📊 完整环境变量清单

| 分类 | 环境变量 | 默认值 | 说明 |
|------|---------|--------|------|
| **网关** | `GATEWAY_CENTER_TYPE` | `nacos` | 配置中心类型 |
| | `GATEWAY_DISCOVERY_LOCATOR_ENABLED` | `true` | 服务发现定位器 |
| **Nacos** | `NACOS_SERVER_ADDR` | `127.0.0.1:8848` | 服务器地址 |
| | `NACOS_NAMESPACE` | `""` | 命名空间 |
| | `NACOS_FILE_EXTENSION` | `yaml` | 文件扩展名 |
| **Consul** | `CONSUL_HOST` | `localhost` | 主机地址 |
| | `CONSUL_PORT` | `8500` | 端口 |
| | `CONSUL_CONFIG_PREFIX` | `config` | 配置前缀 |
| | `CONSUL_DISCOVERY_ENABLED` | `true` | 服务发现启用 |
| | `CONSUL_HEALTH_CHECK_PATH` | `/actuator/health` | 健康检查路径 |
| | `CONSUL_HEALTH_CHECK_INTERVAL` | `10s` | 健康检查间隔 |
| **Redis** | `REDIS_HOST` | `127.0.0.1` | 主机地址 |
| | `REDIS_PORT` | `6379` | 端口 |
| | `REDIS_PASSWORD` | `` | 密码 |
| | `REDIS_DATABASE` | `0` | 数据库索引 |
| | `REDIS_TIMEOUT` | `5000ms` | 超时时间 |
| | `REDIS_POOL_MAX_ACTIVE` | `8` | 最大连接数 |
| | `REDIS_POOL_MAX_IDLE` | `8` | 最大空闲连接 |
| | `REDIS_POOL_MIN_IDLE` | `0` | 最小空闲连接 |
| **日志** | `LOG_LEVEL_GATEWAY` | `DEBUG` | Gateway 日志级别 |
| | `LOG_LEVEL_NACOS` | `INFO` | Nacos 日志级别 |
| | `LOG_LEVEL_CONSUL` | `INFO` | Consul 日志级别 |

---

*Last Updated: March 10, 2026*
