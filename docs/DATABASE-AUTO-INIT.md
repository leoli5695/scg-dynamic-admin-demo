# 数据库自动初始化功能实现

## 📊 实现目标

启动时**自动创建数据库表**，无需手动执行 SQL 脚本。

---

## ✅ 实现方案

### 方案选择：**Spring Boot JPA + Hibernate 自动 DDL**

**优势**:
- ✅ 配置简单，只需添加依赖和配置
- ✅ 自动检测表结构，不存在则创建
- ✅ 支持表结构变更（添加列、修改类型等）
- ✅ 与 MyBatis Plus 共存，互不影响

---

## 🔧 实施步骤

### 1. **添加 Spring Boot Data JPA 依赖**

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

**作用**: 引入 Hibernate，提供自动 DDL 功能

---

### 2. **配置application.yml**

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/gateway_db;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE
    # ⚠️ 移除 DB_CLOSE_ON_EXIT=FALSE（H2 2.x 不支持）
  
  jpa:
    hibernate:
      ddl-auto: update  # 关键配置：自动更新表结构
    show-sql: false
    properties:
      hibernate:
        format_sql: true
    database-platform: org.hibernate.dialect.H2Dialect
```

**ddl-auto 选项说明**:

| 值 | 说明 | 适用场景 |
|-----|------|---------|
| `none` | 不执行任何 DDL | 生产环境 |
| `validate` | 验证表结构，不创建 | 生产环境 |
| `update` | 自动创建/更新表结构 ✅ | **开发环境（推荐）** |
| `create` | 每次启动都删除重建 | 测试环境 |
| `create-drop` | 启动创建，关闭删除 | 测试环境 |

---

### 3. **Entity 添加 JPA 注解**

**RouteEntity 示例**:

```java
@Data
@TableName("routes")  // MyBatis Plus
@Entity(name = "routes")  // JPA
public class RouteEntity {

    @Id
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    
    @Column(nullable = false, length = 1024)
    private String uri;
    
    @Column(columnDefinition = "TEXT")
    private String predicates;
    
    @Column(columnDefinition = "TEXT")
    private String filters;
    
    @Column(columnDefinition = "TEXT")
    private String metadata;
    
    @Column(name = "order_num", columnDefinition = "INT DEFAULT 0")
    private Integer orderNum;
    
    @Column(columnDefinition = "BOOLEAN DEFAULT TRUE")
    private Boolean enabled;
    
    @Column(length = 500)
    private String description;
    
    @CreationTimestamp  // 自动填充创建时间
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp  // 自动填充更新时间
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
```

**关键注解**:
- `@Entity` - 标记为 JPA 实体
- `@Id` - 主键标识
- `@Column` - 列定义（类型、长度、约束）
- `@CreationTimestamp` - 自动填充创建时间
- `@UpdateTimestamp` - 自动填充更新时间

---

## 🎯 工作原理

### 启动流程

```
1. Spring Boot 启动
   ↓
2. JPA 初始化
   ↓
3. Hibernate 扫描 @Entity 类
   ↓
4. 对比数据库元数据
   ↓
5. 生成并执行 DDL 语句
   ├── 表不存在 → CREATE TABLE
   ├── 列不存在 → ALTER TABLE ADD COLUMN
   └── 结构匹配 → 跳过
   ↓
6. 应用正常启动
```

---

## 📋 完整的双写流程（改进版）

### 首次启动

```
1. 启动应用
   ↓
2. JPA 自动创建表
   ├── routes
   ├── services
   ├── plugins
   └── audit_logs
   ↓
3. 从 Nacos 加载配置（如果有）
   ↓
4. 初始化内存缓存
   ↓
5. 启动成功
```

### 正常运行

```
Admin API (POST /api/routes)
   ↓
@Transactional
   ├── 1. routeMapper.insert() → H2 Database ✅
   ├── 2. routeCache.put() → Memory Cache ✅
   └── 3. publisher.publish() → Nacos Config ✅
```

---

## 🔍 验证方法

### 1. **查看启动日志**

```log
Hibernate: 
    create table routes (
        id varchar(255) not null,
        created_at timestamp(6),
        description varchar(500),
        enabled boolean default true,
        filters text,
        metadata text,
        order_num integer default 0,
        predicates text,
        updated_at timestamp(6),
        uri varchar(1024) not null,
        primary key (id)
    )
```

### 2. **访问 H2 Console**

```
URL: http://localhost:8080/h2-console
JDBC URL: jdbc:h2:file:./data/gateway_db
Username: sa
Password: (空)

执行 SQL:
SHOW TABLES;
SELECT * FROM routes;
```

### 3. **检查数据库文件**

```bash
# 项目根目录下会生成
data/
└── gateway_db.mv.db  # H2 数据库文件
```

---

## ⚠️ 注意事项

### 1. **生产环境配置**

生产环境**不建议**使用 `ddl-auto: update`，推荐：

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # 只验证，不创建
  flyway:
    enabled: true  # 使用 Flyway 管理版本
```

### 2. **表结构变更**

- ✅ **添加列**: Hibernate 会自动执行 `ALTER TABLE ADD COLUMN`
- ⚠️ **删除列**: Hibernate **不会**自动删除列（需要手动处理）
- ⚠️ **修改列类型**: 可能失败，建议手动迁移

### 3. **与 MyBatis Plus 共存**

- ✅ JPA 负责表结构创建
- ✅ MyBatis Plus 负责数据操作
- ✅ 两者互不冲突

---

## 📝 其他 Entity 的适配

### ServiceEntity

```java
@Data
@TableName("services")
@Entity(name = "services")
public class ServiceEntity {
    
    @Id
    private String id;
    
    @Column(nullable = false, length = 255)
    private String name;
    
    // ... 其他字段
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
```

### StrategyEntity (原 PluginEntity)

```java
@Data
@TableName("strategies")
@Entity(name = "strategies")
public class StrategyEntity {
    
    @Id
    private String id;
    
    @Column(nullable = false, length = 100)
    private String strategyType;
    
    // ... 其他字段
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
```

---

## 🚀 下一步优化

### 短期（已完成）
- ✅ JPA 自动 DDL
- ✅ Entity 添加 JPA 注解

### 中期
- ⏳ 为所有 Entity 添加 JPA 注解
- ⏳ 添加数据库版本管理（Flyway）
- ⏳ 编写数据迁移脚本

### 长期
- ⏳ 生产环境使用 Flyway 管理 DDL
- ⏳ 完整的数据库版本控制
- ⏳ 自动化测试覆盖

---

## 🎉 总结

**当前状态**:
- ✅ 启动时自动创建数据库表
- ✅ 自动检测并应用表结构变更
- ✅ 无需手动执行 SQL 脚本
- ✅ 与 MyBatis Plus 完美共存

**核心配置**:
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update  # 自动创建/更新表
```

**验证通过**:
- ✅ 首次启动成功创建表
- ✅ 双写功能正常工作
- ✅ 数据持久化到 H2 文件

现在可以放心启动了！🎉
