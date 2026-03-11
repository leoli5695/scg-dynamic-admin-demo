# Plugin 到 Strategy 重命名指南

## 📝 重命名原因

为了保持术语一致性，将 "Plugin" 统一改为 "Strategy"（策略）。

---

## ✅ 已完成的重命名

### 1. **Entity 类**

**文件**: `PluginEntity.java` → `StrategyEntity.java`

**修改内容**:
```java
// 之前
@TableName("plugins")
public class PluginEntity {
    private String pluginType;
    ...
}

// 之后
@TableName("strategies")
public class StrategyEntity {
    private String strategyType;
    ...
}
```

**变化**:
- ✅ 类名：`PluginEntity` → `StrategyEntity`
- ✅ 表名：`plugins` → `strategies`
- ✅ 字段名：`pluginType` → `strategyType`

---

### 2. **Mapper接口**

**文件**: `PluginMapper.java` → `StrategyMapper.java`

**修改内容**:
```java
// 之前
@Mapper
public interface PluginMapper extends BaseMapper<PluginEntity> {
}

// 之后
@Mapper
public interface StrategyMapper extends BaseMapper<StrategyEntity> {
}
```

**变化**:
- ✅ 接口名：`PluginMapper` → `StrategyMapper`
- ✅ 泛型参数：`PluginEntity` → `StrategyEntity`

---

### 3. **Service 层**

**文件**: `StrategyService.java` (已存在)

**当前状态**:
- ✅ Service 名称已经是 `StrategyService`
- ✅ 使用 `PluginConfig` 作为配置模型
- ⏳ 待添加 H2 双写支持

---

## 📋 待完成的工作

### 1. **更新 StrategyService 的引用**

需要修改的文件:
- `StrategyService.java` - 导入语句和注释
- `StrategyController.java` - 导入语句

### 2. **创建 StrategyConverter**

参考 `RouteConverter` 创建转换器：

```java
@Component
public class StrategyConverter {
    public StrategyEntity toEntity(PluginConfig config) {
        // 转换逻辑
    }
    
    public PluginConfig toConfig(StrategyEntity entity) {
        // 反向转换
    }
}
```

### 3. **启用 H2 双写**

在 `StrategyService` 中添加数据库操作：

```java
@Autowired
private StrategyMapper strategyMapper;

@Autowired
private StrategyConverter strategyConverter;

@Transactional(rollbackFor = Exception.class)
public boolean saveStrategy(PluginConfig config) {
    // 1. 转换为 Entity 并写入 H2
    StrategyEntity entity = strategyConverter.toEntity(config);
    int rows = strategyMapper.insert(entity);
    
    // 2. 更新缓存
    updateCache(config);
    
    // 3. 发布到 Nacos
    publisher.publish(new GatewayPluginsConfig(...));
}
```

---

## 🎯 数据库迁移

### SQL 迁移脚本

```sql
-- 1. 重命名表
RENAME TABLE plugins TO strategies;

-- 2. 重命名字段
ALTER TABLE strategies CHANGE plugin_type strategy_type VARCHAR(255);

-- 3. 或者创建新表并迁移数据
CREATE TABLE strategies AS SELECT * FROM plugins;
```

### MyBatis Plus 配置

无需修改，`@TableName("strategies")` 会自动映射到新表。

---

## 📊 影响范围

### 不受影响的部分
- ✅ `PluginConfig.java` - 保留原名（配置模型）
- ✅ `GatewayPluginsConfig.java` - 保留原名
- ✅ Nacos 配置文件名：`gateway-plugins.json` - 保留原名

### 受影响的部分
- ❌ `PluginEntity` → `StrategyEntity`
- ❌ `PluginMapper` → `StrategyMapper`
- ⏳ `StrategyService` - 需要添加双写支持

---

## 🚀 下一步计划

1. ✅ 完成文件重命名
2. ⏳ 更新所有引用点
3. ⏳ 创建 `StrategyConverter`
4. ⏳ 为 `StrategyService` 添加 H2 双写
5. ⏳ 执行数据库迁移

---

## 📝 注意事项

1. **向后兼容**: 保留 `PluginConfig` 等配置类的名称
2. **渐进式迁移**: 先完成代码重构，再执行数据库迁移
3. **测试验证**: 确保所有功能正常工作
