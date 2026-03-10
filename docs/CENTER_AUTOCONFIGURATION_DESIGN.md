# 统一配置中心架构设计

## 📋 设计思想

采用**统一配置类**模式，将 Nacos 和 Consul 的配置中心和服务发现功能分别集中在两个独立的配置类中管理。

---

## 🏗️ 整体架构

```
┌─────────────────────────────────────────┐
│      Application (Filter/Manager)       │
└─────────────────────────────────────────┘
                ↓ uses
┌───────────────────┴─────────────────────┐
│   ConfigCenterService                   │
│   DiscoveryService                      │
└───────────────────┬─────────────────────┘
                    ↑ implemented by
┌───────────────────┴─────────────────────┐
│  NacosCenterAutoConfiguration           │
│  - @ConditionalOnProperty(nacos)        │
│  - Creates: ConfigService               │
│            NamingService                │
│            NacosConfigService           │
│            NacosDiscoveryService        │
└─────────────────────────────────────────┘

┌───────────────────┴─────────────────────┐
│  ConsulCenterAutoConfiguration          │
│  - @ConditionalOnProperty(consul)       │
│  - Creates: ConsulClient                │
│            ConsulConfigService          │
│            ConsulDiscoveryService       │
└─────────────────────────────────────────┘
```

---

## 📁 包结构

```
com.example.gateway.config/
├── NacosCenterAutoConfiguration.java    # Nacos 统一配置类
└── ConsulCenterAutoConfiguration.java   # Consul 统一配置类

com.example.gateway.center/
├── ConfigCenterService.java             # 配置中心接口
├── AbstractConfigService.java           # 抽象基类
├── NacosConfigService.java              # Nacos 实现（无 @Service）
└── ConsulConfigService.java             # Consul 实现（无 @Service）

com.example.gateway.discovery/
├── DiscoveryService.java                # 服务发现接口
├── AbstractDiscoveryService.java        # 抽象基类
├── NacosDiscoveryService.java           # Nacos 实现（无 @Service）
└── ConsulDiscoveryService.java          # Consul 实现（无 @Service）
```

---

## ⚙️ 配置方式

### application.yml - 使用 Nacos（默认）
```yaml
gateway:
  center:
    type: nacos  # 或 consul

spring:
  cloud:
    nacos:
     config:
        server-addr: 127.0.0.1:8848
        namespace: dev
```

### application.yml - 使用 Consul
```yaml
gateway:
  center:
    type: consul

spring:
  cloud:
   consul:
      host: localhost
      port: 8500
     config:
        prefix: config
```

---

## 🔧 核心配置类

### NacosCenterAutoConfiguration

**职责：**
- 创建 `ConfigService` Bean（Nacos 原生客户端）
- 创建 `NamingService` Bean（Nacos 原生客户端）
- 创建 `NacosConfigService` Bean（包装层）
- 创建 `NacosDiscoveryService` Bean（包装层）

**条件注解：**
```java
@ConditionalOnProperty(
    name = "gateway.center.type", 
    havingValue = "nacos", 
    matchIfMissing = true  // 默认使用 Nacos
)
```

### ConsulCenterAutoConfiguration

**职责：**
- 创建 `ConsulClient` Bean（Consul 原生客户端）
- 创建 `ConsulConfigService` Bean（包装层）
- 创建 `ConsulDiscoveryService` Bean（包装层，使用 Spring Cloud DiscoveryClient）

**条件注解：**
```java
@ConditionalOnProperty(
    name = "gateway.center.type", 
    havingValue = "consul"
)
```

---

## 🎯 设计优势

### 1. **集中管理**
- ✅ 所有 Nacos 相关 Bean 在一个类中管理
- ✅ 所有 Consul 相关 Bean 在一个类中管理
- ✅ 避免分散的 `@ConditionalOnProperty` 注解

### 2. **互斥加载**
- ✅ Nacos 和 Consul 配置类互斥加载
- ✅ 只有一套 Bean 会被创建
- ✅ 内存和资源优化

### 3. **依赖清晰**
- ✅ 配置类显式声明依赖关系
- ✅ Bean 创建顺序由 Spring 管理
- ✅ 构造函数注入依赖

### 4. **易于扩展**
- ✅ 新增配置中心只需添加新配置类
- ✅ 不影响现有代码
- ✅ 符合开闭原则

---

## 📊 Bean 生命周期

```
Spring 容器启动
    ↓
读取 gateway.center.type 配置
    ↓
加载对应的配置类（Nacos/Consul）
    ↓
创建底层客户端 Bean
    ├─ ConfigService / ConsulClient
    └─ NamingService
    ↓
创建包装层 Bean
    ├─ NacosConfigService/ ConsulConfigService
    └─ NacosDiscoveryService / ConsulDiscoveryService
    ↓
应用其他组件注入这些 Bean
    └─ StaticProtocolGlobalFilter 等
```

---

## 💡 使用示例

### 注入配置中心服务
```java
@Component
public class MyConfigManager {
    
    private final ConfigCenterService configService;
    
    @Autowired
    public MyConfigManager(ConfigCenterService configService) {
        this.configService = configService;
    }
    
    public String loadConfig() {
        // 自动注入正确的实现（Nacos 或 Consul）
        return configService.getConfig("my-config", "DEFAULT_GROUP");
    }
}
```

### 注入服务发现
```java
@Component
public class MyServiceCaller {
    
    private final DiscoveryService discoveryService;
    
    @Autowired
    public MyServiceCaller(DiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }
    
    public void callService() {
        // 自动获取健康实例
        List<ServiceInstance> instances = 
            discoveryService.getHealthyInstances("my-service");
    }
}
```

---

## ⚠️ 注意事项

1. **配置前缀** - 确保 `gateway.center.type` 配置正确
2. **默认值** - 未配置时默认使用 Nacos
3. **Bean 名称** - 避免在两个配置类中使用相同的 Bean 名称
4. **依赖注入** - 使用构造函数注入而非字段注入

---

*Created: March 10, 2026*  
*Status: Architecture Optimized*
