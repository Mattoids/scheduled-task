# 定时任务报表系统

基于 Spring Boot 3 + MyBatis-Plus + Quartz + Spring Security + JWT 的定时任务报表系统。

## 核心功能

- 通过 MySQL 配置定时任务，支持一次性 / CRON 周期性触发
- 支持手动触发任务
- 多数据源管理，数据源配置存储在数据库中
- 数据源连接支持 SSH 隧道代理
- 根据 SQL 查询结果生成报表
- 支持 Excel / Word / PPT / CSV / TXT 多种模板
- 报表生成后通过 SMTP 发送到指定收件人
- 发件邮箱、收件人 / 收件组独立管理
- 基于 RBAC 的权限控制

## 技术栈

- Java 17
- Spring Boot 3.2.5
- MyBatis-Plus 3.5.5
- Quartz
- Spring Security + JWT
- Apache POI（Office 文件处理）
- Apache Commons CSV
- JSch（SSH 代理）

## 快速开始

### 1. 初始化数据库

```sql
-- 执行 src/main/resources/db/schema.sql
```

### 2. 配置数据库连接

编辑 `src/main/resources/application-dev.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/scheduled_task?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: root
    password: root
```

### 3. 编译运行

```bash
mvn clean package
java -jar target/scheduled-task-1.0.0-SNAPSHOT.jar
```

### 4. 默认账号

- 用户名：`admin`
- 密码：`admin123`

登录接口：`POST /api/auth/login`

## 主要接口

| 功能 | 接口 |
|------|------|
| 登录 | `POST /api/auth/login` |
| 当前用户 | `GET /api/auth/me` |
| 任务分页 | `GET /api/task/page` |
| 创建任务 | `POST /api/task` |
| 手动触发 | `POST /api/task/{id}/trigger` |
| 数据源分页 | `GET /api/datasource/page` |
| 测试数据源 | `POST /api/datasource/{id}/test` |
| 邮箱配置分页 | `GET /api/email-config/page` |
| 收件人管理 | `/api/email-recipient/**` |
| 模板上传 | `POST /api/template/upload` |
| 任务日志 | `GET /api/task-log/page` |
| 用户 / 角色 / 权限 | `/api/system/**` |

## 模板说明

模板中支持 `${字段名}` 占位符，Excel 模板第一行应为字段头（使用 `${字段名}` 或纯字段名均可），系统会从第二行开始填充 SQL 查询结果。

示例 Excel 模板第一行：

```
${id} | ${name} | ${create_time}
```

## 文件命名

任务中可配置 `fileNamePattern`，支持以下占位符：

- `{yyyyMMddHHmmss}`
- `{yyyyMMdd}`
- `{yyyy-MM-dd}`
- `{HHmmss}`

## 数据源 SSH 代理

在数据源配置中开启 `sshEnabled` 并填写 SSH 跳板机信息即可。系统会在本地建立 SSH 隧道，再通过 127.0.0.1:localPort 连接目标数据库。

## 注意事项

- 项目使用简单 AES 加密存储密码，生产环境建议更换密钥或使用 KMS。
- 定时任务使用 Quartz RAMJobStore，重启后会从数据库重新加载所有启用状态的任务。
