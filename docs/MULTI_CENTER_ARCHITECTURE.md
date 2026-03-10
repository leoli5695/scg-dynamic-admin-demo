# 多配置/注册中心架构设计文档

## 📋 设计目标

支持在 **Nacos** 和 **Consul** 之间切换，通过配置 `gateway.center.type` 控制使用哪个配置/注册中心。

---

## 🏗️ 整体架构

```
┌─────────────────────────────────────────────────┐
│          Application Layer(Filter/Manager)     │
└─────────────────────────────────────────────────┘
                    ↓ uses
┌─────────────────────────────────────────────────┐
│        ConfigCenterService (Interface)          │
│        DiscoveryService (Interface)             │
└─────────────────────────────────────────────────┘
                    ↑ implements
┌───────────────────┴─────────────────────────────┐
│      AbstractConfigService (Abstract Class)     │
└───────────────────┬─────────────────────────────┘
                    ↑ extends
┌───────────────────┴─────────────────────────────┐
│  NacosConfigServiceImpl   ConsulConfigServiceImpl
│  NacosDiscoveryService    ConsulDiscoveryService
└─────────────────────────────────────────────────┘
                    ↑ conditional
┌───────────────────┴─────────────────────────────┐
│    @ConditionalOnProperty(gateway.center.type)  │
└─────────────────────────────────────────────────┘
```

---

## 📁 包结构

```
com.example.gateway/
├── center/                        # 配置中心模块
│   ├── CenterType.java          # 枚举：NACOS / CONSUL
│   ├── ConfigCenterService.java  # 配置中心接口
│   ├── AbstractConfigService.java # 抽象基类（公共逻辑）
│   ├── NacosConfigServiceImpl.java # Nacos 实现
│   └── ConsulConfigServiceImpl.java # Consul 实现
│
├── discovery/                     # 服务发现模块
│   ├── DiscoveryService.java    # 服务发现接口
│   ├── AbstractDiscoveryService.java # 抽象基类
│   ├── NacosDiscoveryService.java # Nacos 实现
│   └── ConsulDiscoveryService.java # Consul 实现
│
└── config/                        # 自动配置模块
    ├── CenterAutoConfiguration.java # 配置中心自动装配
    └── DiscoveryAutoConfiguration.java # 服务发现自动装配
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
      discovery:
        server-addr: 127.0.0.1:8848
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
      discovery:
        enabled: true
```

---

## 🔧 核心接口

### ConfigCenterService
```java
public interface ConfigCenterService {
   String getConfig(String dataId, String group);
    Map<String, Object> getAllConfigData(String dataId, String group);
    void addListener(String dataId, String group, ConfigListener listener);
    void removeListener(String dataId, String group, ConfigListener listener);
    boolean publishConfig(String dataId, String group, String content);
    CenterType getCenterType();
    void shutdown();
    
    interface ConfigListener {
        void onConfigChange(String dataId, String group, String newContent);
    }
}
```

### DiscoveryService
```java
public interface DiscoveryService {
    List<ServiceInstance> getInstances(String serviceName);
    List<ServiceInstance> getHealthyInstances(String serviceName);
    ServiceInstance selectInstance(String serviceName, String strategy);
    void register(ServiceInstance instance);
    void deregister(ServiceInstance instance);
    CenterType getCenterType();
    void shutdown();
    
    class ServiceInstance {
        private String instanceId;
        private String serviceName;
        private String ip;
        private int port;
        private boolean healthy;
        private double weight;
        private Map<String, String> metadata;
    }
}
```

---

## 🎯 条件装配

### NacosConfigServiceImpl
```java
@Service
@ConditionalOnProperty(
    name = "gateway.center.type", 
    havingValue = "nacos", 
   matchIfMissing = true  // 默认使用 Nacos
)
public class NacosConfigServiceImpl extends AbstractConfigService {
    // ...
}
```

### ConsulConfigServiceImpl
```java
@Service
@ConditionalOnProperty(
    name = "gateway.center.type", 
    havingValue = "consul"
)
public class ConsulConfigServiceImpl extends AbstractConfigService {
    // ...
}
```

---

## 🚀 使用示例

### 1. 注入配置中心服务
```java
@Component
public class MyConfigManager {
    
    private final ConfigCenterService configService;
    
    @Autowired
    public MyConfigManager(ConfigCenterService configService) {
        this.configService = configService;
    }
    
    public void loadConfig() {
        // 获取配置
       String config = configService.getConfig("my-config", "DEFAULT_GROUP");
        
        // 添加监听器
       configService.addListener("my-config", "DEFAULT_GROUP", (dataId, group, newContent) -> {
            log.info("Configuration changed: {}", newContent);
        });
    }
}
```

### 2. 注入服务发现
```java
@Component
public class MyServiceCaller {
    
    private final DiscoveryService discoveryService;
    
    @Autowired
    public MyServiceCaller(DiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }
    
    public void callService() {
        // 获取健康实例
        List<ServiceInstance> instances= discoveryService.getHealthyInstances("my-service");
        
        // 负载均衡选择实例
        ServiceInstance selected = discoveryService.selectInstance("my-service", "round-robin");
        
        // 调用服务
       String url = String.format("http://%s:%d/api", selected.getIp(), selected.getPort());
       restTemplate.getForObject(url, String.class);
    }
}
```

---

## 📊 对比矩阵

| 功能 | Nacos | Consul |
|------|-------|--------|
| **配置管理** | ✅ 原生支持 | ✅ KV Store |
| **服务发现** | ✅ 原生支持 | ✅ 原生支持 |
| **配置监听** | ✅ 长轮询 | ✅ Watch API |
| **健康检查** | ✅ 心跳 | ✅ TCP/HTTP |
| **多数据中心** | ✅ 支持 | ✅ 强项 |
| **一致性协议** | Raft | Raft |
| **性能** | 高 | 中 |
| **国内生态** | 阿里系，文档丰富 | HashiCorp，国际通用 |

---

## 💡 设计优势

1. **开闭原则** - 新增配置中心只需实现接口，无需修改现有代码
2. **依赖倒置** - 上层业务依赖抽象接口，不依赖具体实现
3. **单一职责** - 每个类只负责一件事（配置/发现）
4. **策略模式** - 根据配置动态选择实现
5. **工厂模式** - Spring 容器自动装配正确的 Bean

---

## ⚠️ 注意事项

1. **互斥性** - Nacos 和 Consul 的 Bean 通过 `@ConditionalOnProperty` 互斥加载
2. **配置前缀** - 确保 `gateway.center.type` 配置正确
3. **依赖管理** - Maven 已同时包含 Nacos 和 Consul 依赖
4. **默认值** - 未配置时默认使用 Nacos

---

*Created: March 10, 2026*  
*Status: Architecture Design Complete*
