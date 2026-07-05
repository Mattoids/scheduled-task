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

## 通知系统

任务执行后，系统可根据配置的通知规则通过多种渠道发送通知。

### 通知渠道

| 渠道 | 说明 |
|------|------|
| `EMAIL` | 通过 SMTP 发送邮件，支持 HTML 正文和附件 |
| `WECOM_APP` | 企业微信应用消息，支持文本和文件 |
| `WECOM_BOT` | 企业微信机器人 Webhook 消息，支持 @提及 |
| `WECOM_INTELLIGENT_BOT` | 企业微信智能机器人 Webhook 消息 |

### 通知规则

通知规则基于事件驱动，支持以下触发事件：

- `TASK_SUCCESS` — 任务执行成功
- `TASK_FAILURE` — 任务执行失败
- `TASK_COMPLETED` — 任务执行完成（无论成功或失败）

规则可全局生效（`task_id` 为空），也可绑定到指定任务。

### 通知模板占位符

邮件主题、邮件正文、企业微信文本模板均支持占位符，使用 `${变量名}` 格式。

**内置变量**（同任务级变量规则）：

| 变量 | 说明 | 示例（当前 2026-07-04） |
|------|------|------------------------|
| `{lastMonth}` | 上月月份，固定两位 | `06` |
| `{lastMonth:yyyyMM}` | 上月按自定义格式输出 | `202606` |
| `{nextMonth}` | 下月月份，固定 `yyyyMM` | `202608` |
| `{nextMonth:yyyy-MM}` | 下月按自定义格式输出 | `2026-08` |
| `{yyyyMMddHHmmss}` | 当前时间 | `20260704123045` |
| `{yyyyMMdd}` | 当前日期 | `20260704` |
| `{yyyy-MM-dd}` | 当前日期 | `2026-07-04` |
| `{HHmmss}` | 当前时间 | `123045` |

**内联 SQL 结果占位符**：任务关联的 SQL 查询结果会作为变量注入模板。

- 单行单列：直接使用该列名，值为字符串/数字
- 多行或多列：使用列名作为变量，值为数组

例如 SQL 查询结果为两行两列 `name` 和 `value`：

```json
[{"name": "Alice", "value": 100}, {"name": "Bob", "value": 200}]
```

模板中可直接使用：

```
本周报表：${name} 共 ${value} 条记录
总记录数：${name_count}
```

会渲染为：

```
本周报表：[Alice, Bob] 共 [100, 200] 条记录
总记录数：2
```

### AI 优化通知内容

在通知规则中启用 `aiOptimizeNotify`，系统会调用 AI 配置中指定的模型对通知内容（主题 / 正文）进行优化，使表达更自然、更精炼。

所有通知渠道均支持 AI 优化：EMAIL、WECOM_APP、WECOM_BOT、WECOM_INTELLIGENT_BOT。

### 企业微信文件发送策略

对于 WeCom 渠道，文件发送有两种模式：

- **直接发送文件**：未配置存储配置时，文件作为附件直接发送
- **上传后发送链接**：在通知规则中指定存储配置，系统先将文件上传到存储系统，再发送下载链接。适用于文件较大或需要长期留存的场景

## 存储配置

系统支持多种文件存储后端，用于报表文件与通知附件的上传与分发：

| 存储类型 | 说明 |
|---------|------|
| `LOCAL` | 本地文件系统 |
| `OSS` | 阿里云 OSS |
| `S3` | AWS S3 及兼容存储 |
| `WEBDAV` | WebDAV 服务器 |

配置存储后，通知规则可选择使用该存储将文件上传并返回下载链接。

## AI 配置

系统支持多种 AI 厂商接入，用于通知内容优化：

| 厂商 | 说明 |
|------|------|
| `OPENAI` | OpenAI API |
| `ANTHROPIC` | Anthropic Claude API |
| `AZURE_OPENAI` | Azure OpenAI Service |
| `OLLAMA` | 本地 Ollama 部署 |
| `CUSTOM` | 自定义兼容 OpenAI 格式的 API |

## SQL 模块

一个任务可以关联一条或多条 SQL。每条 SQL 独立配置数据源、输出格式、文件名规则与模板。

### SQL 分组

SQL 配置支持 `group_name` 分组：

- 在 SQL 管理中填写分组名称，如 `门店报表`、`财务日报`
- SQL 管理列表支持按分组筛选
- 任务配置中选择 SQL 时，下拉框会按分组展示，便于在 SQL 较多时快速定位
- 未填写分组的 SQL 会归入"未分组"

### 单 SQL 输出

未绑定模板时，SQL 结果按 `outputFormat` 输出：

| 格式 | 说明 |
|------|------|
| CSV | 默认，使用 SQL 列别名作为表头 |
| EXCEL | 生成 `.xlsx`，使用 SQL 列别名作为表头 |
| TXT | 按模板中 `${字段名}` 逐行渲染 |
| WORD / PPT | 无模板时无法生成有效文件，自动回退为 CSV |

### 多 SQL 链式处理（模板链）

当多条 SQL 绑定到同一个模板时，系统会按顺序执行：

1. 第 1 条 SQL 的结果填充模板，生成临时文件
2. 第 2 条 SQL 的结果继续填充上一步的临时文件
3. ...
4. 最后一条 SQL 生成最终报表文件

该机制常用于 Word / PPT 中先替换基础信息，再展开明细表格。

## 模板说明

模板中使用 `${字段名}` 作为占位符，系统会用 SQL 查询结果进行替换或展开。

> 字段名区分大小写，需与 SQL 列名/列别名完全一致。

### 通用占位符

| 占位符 | 说明 |
|--------|------|
| `${字段名}` | SQL 查询结果中的字段值 |
| `${序号}` / `${seq}` | 自动生成从 1 开始的序号（表格展开时） |

### Excel 模板

- 第一行为表头，可使用 `${字段名}` 或纯字段名
- 系统从第二行开始按 SQL 结果逐行填充
- 表头下方无需预留样例行

示例：

| 城市 | 门店 | 打卡次数 |
|------|------|----------|
| `${city_name}` | `${store_name}` | `${checkin_num}` |

或：

```
city_name | store_name | checkin_num
```

### Word 模板

- 段落中可直接使用 `${字段名}` 替换单行数据
- 表格中第一行使用 `${字段名}` 作为表头，系统会自动识别并展开数据行
- 支持只有表头没有样例行的表格
- 多 SQL 链式处理时，未匹配占位符会保留给下一条 SQL 处理

示例段落：

```
您好，本月共产生 ${total} 条打卡记录。
```

示例表格（第一行为表头）：

| 城市 | 门店 | 打卡次数 |
|------|------|----------|
| `${city_name}` | `${store_name}` | `${checkin_num}` |

### PPT 模板

- 文本框中可直接使用 `${字段名}` 替换
- 表格展开逻辑与 Word 类似
- 多行数据（>1 行）时才会展开表格；单行数据按普通占位符替换

### CSV 模板

- 第一行为表头，使用字段名（可带 `${}`）
- 输出时保持表头，按字段顺序填充数据

### TXT 模板

- 整份文件作为一行模板，使用 `${字段名}` 占位
- 每条 SQL 结果渲染为一行输出

示例模板：

```
城市:${city_name}, 门店:${store_name}, 次数:${checkin_num}
```

## 变量与占位符规则

以下配置项支持使用时间变量：

- **任务级文件名格式**（`task_config.file_name_pattern`）：当多个 SQL 共享同一个模板形成链式处理时，最终输出文件名优先使用任务级配置
- **SQL 级文件名格式**（`task_sql_config.file_name_pattern`）：单 SQL 输出时使用；若任务级未配置，链式处理时也会回退到第一条 SQL 的配置
- 通知规则中的 **邮件主题**（`subject`）
- 通知规则中的 **邮件正文**（`body`）
- 通知规则中的 **企业微信文本模板**（`content`）

### 内置变量

| 变量 | 说明 | 示例（当前 2026-07-04） |
|------|------|------------------------|
| `{lastMonth}` | 上月月份，固定两位 | `06` |
| `{lastMonth:格式}` | 上月按自定义格式输出 | `{lastMonth:yyyyMM}` → `202606` |
| `{nextMonth}` | 下月月份，固定 `yyyyMM` | `202608` |
| `{nextMonth:格式}` | 下月按自定义格式输出 | `{nextMonth:yyyy-MM}` → `2026-08` |
| `{yyyyMMddHHmmss}` | 当前时间 | `20260704123045` |
| `{yyyyMMdd}` | 当前日期 | `20260704` |
| `{yyyy-MM-dd}` | 当前日期 | `2026-07-04` |
| `{HHmmss}` | 当前时间 | `123045` |
| `{yyyy}` / `{MM}` / `{dd}` 等 | Java `DateTimeFormatter` 支持的任意格式 | - |

> 不认识的 `{...}` 内容会原样保留。

### 文件名变量示例

单 SQL 输出：

```
report_{yyyyMMddHHmmss}.csv
```

模板链输出（任务级文件名）：

```
{lastMonth}月门店打卡报表_{yyyyMMdd}.xlsx
```

实际输出：

```
06月门店打卡报表_20260704.xlsx
```

### 邮件主题/正文变量示例

邮件主题：

```
{lastMonth}月门店打卡报表
```

邮件正文：

```
您好，附件为 {lastMonth} 月门店打卡报表，统计周期 {lastMonth:yyyy-MM-01} ~ {lastMonth:yyyy-MM-dd}，请查收。
```

## 数据源 SSH 代理

在数据源配置中开启 `sshEnabled` 并填写 SSH 跳板机信息即可。系统会在本地建立 SSH 隧道，再通过 127.0.0.1:localPort 连接目标数据库。

## 主要接口

| 功能 | 接口 |
|------|------|
| 登录 | `POST /api/auth/login` |
| 当前用户 | `GET /api/auth/me` |
| 修改密码 | `POST /api/auth/change-password` |
| 仪表盘统计 | `GET /api/dashboard/stats` |
| 任务分页 | `GET /api/task/page` |
| 创建任务 | `POST /api/task` |
| 修改任务 | `PUT /api/task/{id}` |
| 更新任务状态 | `PUT /api/task/{id}/status` |
| 手动触发任务 | `POST /api/task/{id}/trigger` |
| 任务日志（按任务） | `GET /api/task/{taskId}/logs` |
| 任务日志分页 | `GET /api/task-log/page` |
| SQL 配置分页 | `GET /api/task-sql/page` |
| SQL 配置列表（下拉） | `GET /api/task-sql/list` |
| SQL 分组分页 | `GET /api/task-sql-group/page` |
| SQL 分组列表 | `GET /api/task-sql-group/list` |
| 数据源分页 | `GET /api/datasource/page` |
| 测试数据源 | `POST /api/datasource/{id}/test` |
| 收件人分页 | `GET /api/email-recipient/page` |
| 收件人列表 | `GET /api/email-recipient/list` |
| 收件人群组列表 | `GET /api/email-recipient/group/list` |
| 模板分页 | `GET /api/template/page` |
| 模板上传 | `POST /api/template/upload` |
| 通知配置分页 | `GET /api/notification-config/page` |
| 测试通知配置 | `POST /api/notification-config/test` |
| 通知规则分页 | `GET /api/notification-rule/page` |
| 通知规则列表 | `GET /api/notification-rule/list` |
| AI 配置分页 | `GET /api/ai-config/page` |
| AI 配置列表 | `GET /api/ai-config/list` |
| 测试 AI 配置 | `POST /api/ai-config/test` |
| 存储配置分页 | `GET /api/storage-config/page` |
| 存储配置列表 | `GET /api/storage-config/list` |
| 测试存储配置 | `POST /api/storage-config/{id}/test` |
| 存储文件访问 | `GET /storage/**` |
| 企业微信回调（GET/POST） | `/api/wecom/callback/{configId}` |
| AI 意图解析 | `POST /api/assistant/parse-intent` |
| AI 优化通知 | `POST /api/assistant/optimize-notification` |
| 用户分页 | `GET /api/system/user/page` |
| 角色列表 | `GET /api/system/role/list` |
| 权限列表 | `GET /api/system/permission/list` |

## 仪表盘

首页仪表盘提供系统资源与执行状态的一览：

- **资源概览卡片**：任务、数据源、邮箱配置、报表模板数量，点击卡片可直接跳转对应管理页
- **执行统计卡片**：今日执行次数、累计成功 / 失败次数
- **任务启用情况**：实时展示启用 / 停用任务占比
- **今日执行成功率**：成功 / 失败 / 执行中分布
- **最近执行日志**：展示最近 10 条任务执行记录，含任务名称、触发方式、状态、耗时、结果
- **快捷操作**：一键新建任务、进入 SQL 管理、进入数据源管理

## 注意事项

- 项目使用简单 AES 加密存储密码，生产环境建议更换密钥或使用 KMS。
- 定时任务使用 Quartz RAMJobStore，重启后会从数据库重新加载所有启用状态的任务。
- 邮件正文支持 HTML，可直接在 `body` 中编写 `<p>`、`<table>` 等标签。