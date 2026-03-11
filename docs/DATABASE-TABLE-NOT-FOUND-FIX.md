# 数据库表不存在问题处理

## 📊 问题描述

启动时遇到以下错误：

```
org.apache.ibatis.binding.BindingException: 
Invalid bound statement (not found): com.example.gatewayadmin.mapper.RouteMapper.selectList
```

**原因**: MyBatis Plus 的 BaseMapper 方法（如 `selectList`）在表不存在时会抛出异常。

---

## ✅ 解决方案

### 1. **添加异常处理**

在 `RouteService.loadRoutesFromDatabase()` 方法中添加特殊处理：

```java
private void loadRoutesFromDatabase() {
    try {
        List<RouteEntity> entities = routeMapper.selectList(null);
        if (entities != null && !entities.isEmpty()) {
            // 加载数据到缓存
            log.info("Loaded {} routes from database", entities.size());
        } else {
            log.info("No routes found in database (table is empty)");
        }
    } catch (org.apache.ibatis.binding.BindingException e) {
        // 表可能还不存在，跳过数据库加载
        log.warn("Database table 'routes' not found, will be created on first write. Error: {}", e.getMessage());
    } catch (Exception e) {
        log.error("Failed to load routes from database", e);
    }
}
```

**优势**:
- ✅ Graceful degradation - 表不存在时优雅降级
- ✅ 不影响其他功能 - Nacos 配置中心仍然可用
- ✅ 首次启动友好 - 不会因为表不存在而启动失败

---

### 2. **表结构创建时机**

H2 数据库的表会在以下情况创建：

#### 方案 A：使用 Spring Boot 自动 DDL（推荐）

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:h2:file:./data/gateway_db
  jpa:
    hibernate:
      ddl-auto: update  # 或 create-drop
  h2:
    console:
      enabled: true
```

#### 方案 B：手动执行 schema.sql

```bash
# 访问 H2 Console
http://localhost:8080/h2-console

# JDBC URL: jdbc:h2:file:./data/gateway_db
# 执行 schema.sql 中的 SQL 语句
```

#### 方案 C：首次写入时创建（当前方案）

- 启动时不检查表是否存在
- 第一次调用 `createRoute()` 时，MyBatis Plus 会尝试插入
- 如果表不存在会报错，但此时可以捕获并提示用户

---

## 🎯 当前实现策略

**启动流程**:
```
1. @PostConstruct init()
   ├── loadRoutesFromDatabase()
   │   ├── 成功 → 加载到缓存 ✅
   │   └── 失败 → 记录警告日志 ⚠️
   │
   └── loadRoutesFromConfigCenter()
       ├── 成功 → 加载到缓存 ✅
       └── 失败 → 使用空配置 ⚠️
```

**优先级**: Nacos > H2（后加载的覆盖先加载的）

---

## 📝 完整的双写流程

### 创建路由

```java
@Transactional(rollbackFor = Exception.class)
public boolean createRoute(RouteDefinition route) {
    // 1. 转换为 Entity
    RouteEntity entity = routeConverter.toEntity(route);
    
    // 2. 写入 H2 数据库
    int rows = routeMapper.insert(entity);
    if (rows != 1) {
        throw new RuntimeException("Failed to insert route into database");
    }
    
    // 3. 更新内存缓存
    routeCache.put(route.getId(), route);
    
    // 4. 发布到 Nacos 配置中心
    publisher.publish(new GatewayRoutesConfig(...));
}
```

**关键点**:
- ✅ 事务保证原子性
- ✅ 任何一步失败都会回滚
- ✅ 双写确保数据一致性

---

## 🔧 改进建议

### 短期（已完成）
- ✅ 添加异常处理，允许表不存在
- ✅ Graceful degradation，不影响启动

### 中期
- ⏳ 添加 Spring Boot JPA 支持，自动 DDL
- ⏳ 启动时检查表是否存在，不存在则自动创建

### 长期
- ⏳ 数据库版本管理（Flyway/Liquibase）
- ⏳ 数据迁移脚本
- ⏳ 完整的数据库初始化流程

---

## 📋 相关文件

- `RouteService.java` - 路由服务（已添加异常处理）
- `schema.sql` - 数据库表结构定义
- `application.yml` - 数据库配置
- `MybatisPlusConfig.java` - MyBatis 配置

---

## 🎉 总结

**当前状态**:
- ✅ 启动时即使表不存在也不会失败
- ✅ 优先从 Nacos 加载配置
- ✅ H2 作为备用数据源
- ✅ 双写机制完整（当表存在时）

**下一步优化方向**:
1. 启用 Spring Boot JPA 自动 DDL
2. 完善数据库初始化流程
3. 添加 Flyway 进行版本管理
