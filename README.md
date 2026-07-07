# 定时任务报表系统

基于 Spring Boot 3 + MyBatis-Plus + Quartz + Spring Security + JWT 的定时任务报表系统。

## 核心功能

- 通过 MySQL 配置定时任务，支持一次性 / CRON 周期性触发
- 支持手动触发任务
- 多数据源管理，数据源配置存储在数据库中
- 数据源连接支持 SSH 隧道代理
- 根据 SQL 查询结果生成报表
- 支持 Excel / Word / PPT / CSV / TXT 多种模板
- 支持按 SQL 结果生成图表（柱状图 / 折线图 / 饼图 / 面积图 / 散点图 / 堆叠柱状图 / 环形图）并插入通知内容
- 报表生成后通过 SMTP 发送到指定收件人
- 发件邮箱、收件人 / 收件组独立管理
- 基于 RBAC 的权限控制

## 技术栈

- Java 17
- Spring Boot 3.2.5
- MyBatis-Plus 3.5.7
- Quartz
- Spring Security + JWT
- Apache POI（Office 文件处理）
- Apache Commons CSV
- sshj（SSH 代理）
- XChart（图表生成）

## 快速开始

### 1. 初始化数据库

项目使用 Flyway 自动管理数据库迁移，首次启动时会自动执行 `src/main/resources/db/migration` 下的脚本，无需手动导入 schema.sql。

如需手动初始化，可执行 `src/main/resources/db/migration` 目录下的迁移脚本。

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
| `EMAIL` | 通过 SMTP 发送邮件，支持 HTML 正文、内联图片和附件 |
| `WECOM_APP` | 企业微信应用消息，支持 Markdown 富文本、文件和图片 |
| `WECOM_BOT` | 企业微信机器人 Webhook 消息，支持 Markdown 富文本、文件、图片和 @提及 |
| `WECOM_INTELLIGENT_BOT` | 企业微信智能机器人 Webhook 消息，支持 Markdown 富文本、文件和图片 |
| `DINGTALK` | 钉钉机器人 Webhook 消息 |
| `FEISHU` | 飞书机器人 Webhook 消息 |
| `SLACK` | Slack Webhook 消息 |
| `WEBHOOK` | 自定义 Webhook 通知 |

### 通知规则

通知规则基于事件驱动，支持以下触发事件：

- `TASK_SUCCESS` — 任务执行成功
- `TASK_FAILURE` — 任务执行失败
- `TASK_COMPLETED` — 任务执行完成（无论成功或失败）

规则可全局生效（`task_id` 为空），也可绑定到指定任务。

### 企业微信消息格式

企业微信渠道（`WECOM_APP`、`WECOM_BOT`、`WECOM_INTELLIGENT_BOT`）发送 **Markdown 富文本** 消息。

**格式说明：**

- 第一行作为标题，显示为蓝色加粗文字
- 剩余内容作为正文，支持基本 Markdown 语法
- 标题和正文之间用空行分隔

**示例正文模板：**

```
本周任务执行报告
> 任务: 销售日报任务
> 状态: 执行成功
> 耗时: 120s

本次共处理 **${name_count}** 条记录。
```

企业微信机器人消息正文中支持 `@用户`，在通知规则的 `wecomToUser` 字段中填写需要 @ 的 userId。

### 通知模板占位符

邮件主题、邮件正文、企业微信文本模板均支持占位符，使用 `${变量名}` 格式。

**图表占位符**：在 SQL 中启用图表生成后，可通过 `${chart:sql编码}` 在通知内容任意位置插入对应图表。

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

### 企业微信指令

企业微信应用 / 智能机器人（CALLBACK 模式）支持通过消息指令操作系统：

| 指令 | 示例 | 说明 |
|------|------|------|
| `帮助` | `帮助` | 显示可用指令 |
| `任务列表` | `任务列表` | 查看任务列表 |
| `运行 {ID}` | `运行 1` | 按 ID 运行任务 |
| `运行 {任务名称}` | `运行 销售日报` | 按名称匹配任务并运行 |
| `运行 {任务名称} {时间范围}` | `运行 销售日报 昨天` | 按指定时间范围运行任务 |
| `查询 {关键词}` | `查询 销售数据` | 直接查询 SQL 数据 |

**时间范围**：支持 `昨天`、`今天`、`上周`、`本周`、`上个月`、`本月`、`上季度`、`本季度`、`今年`、`最近 N 天` 等。

在任务关联的 SQL 中，可通过 `${startTime}` 和 `${endTime}` 引用解析后的时间范围（格式 `yyyy-MM-dd HH:mm:ss`），从而按指定区间生成报表或图表。

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

### SQL 图表生成

系统支持为每条 SQL 独立生成 PNG 图表，并插入到邮件或企业微信通知的任意位置。图表配置位于「SQL 管理」中，与 SQL 一一对应，因此一个任务下的多条 SQL 可以分别生成不同图表。

#### 配置项

| 配置项 | 说明 |
|--------|------|
| `chart_enabled` | 是否启用图表生成：`1` 启用，`0` 禁用 |
| `chart_type` | 图表类型，支持 `BAR`（柱状图）、`LINE`（折线图）、`PIE`（饼图）、`AREA`（面积图）、`SCATTER`（散点图）、`STACKED_BAR`（堆叠柱状图）、`DOUGHNUT`（环形图） |
| `chart_title` | 图表标题，留空时自动使用 SQL 名称 |

> 图表生成与 `output_format` 无关。只要 SQL 未绑定模板（即非模板链中的 SQL），开启 `chart_enabled` 后任务执行时就会自动生成图表。

#### 数据要求

图表基于 SQL 查询结果自动生成，系统按以下规则识别数据：

- **分类轴（X 轴 / 扇区标签）**：优先选择结果中第一个**非数值**列（如日期、名称、类别）。
- **数值序列**：选择其余所有**数值**列作为 Y 轴数据；多个数值列会生成多个序列。
- **退化场景**：如果结果只有一列且为数值列，系统会自动用行号（1、2、3…）作为分类轴生成图表。

因此建议为图表 SQL 返回如下结构：

```sql
-- 柱状图 / 折线图 / 面积图 / 堆叠柱状图 / 散点图
SELECT category AS '类别', value1 AS '销售额', value2 AS '利润'
FROM sales;

-- 饼图 / 环形图
SELECT region AS '地区', amount AS '销售额'
FROM sales;
```

> 列名会作为图例名称显示，建议为列设置清晰的中文或英文别名。

#### 在通知中引用图表

在邮件正文或企业微信文本模板中，使用占位符：

```
${chart:sql编码}
```

其中 `sql编码` 对应「SQL 管理」中该 SQL 的 `sql_code`。

**完整示例：**

1. 创建 SQL，编码为 `daily_sales`：

```sql
SELECT date AS '日期', amount AS '销售额'
FROM daily_sales
WHERE date BETWEEN '${startTime}' AND '${endTime}';
```

2. 在 SQL 管理中开启「生成图表」，选择「柱状图」，标题留空。

3. 在通知规则中写入正文：

```
本月销售趋势如下：
${chart:daily_sales}

详细数据请查看附件。
```

#### 不同通知渠道的显示效果

| 渠道 | 显示方式 |
|------|----------|
| **EMAIL** | 占位符替换为 `<img src="cid:chart_sql编码" />`，图表以内联图片形式显示在邮件正文中 |
| **WECOM_APP / WECOM_BOT / WECOM_INTELLIGENT_BOT** | 占位符替换为 `[图表: sql编码]` 文本标记，随后自动补发对应的图片消息 |

> 一个任务中多个 SQL 可以同时生成图表，只需使用不同的 `sql编码` 作为占位符，例如 `${chart:daily_sales}`、`${chart:category_sales}`。

#### 各图表类型说明

| 类型 | 适用场景 | 数据要求 |
|------|----------|----------|
| `BAR` | 对比不同类别的数值 | 1 个分类列 + 1 个或多个数值列 |
| `LINE` | 展示趋势变化 | 1 个分类列（通常为日期）+ 1 个或多个数值列 |
| `AREA` | 强调数量累积或趋势 | 同折线图 |
| `SCATTER` | 观察数据分布 | 1 个分类列 + 1 个或多个数值列 |
| `STACKED_BAR` | 展示整体与部分关系 | 1 个分类列 + **多个**数值列 |
| `PIE` | 展示占比 | 1 个分类列 + **1 个**数值列 |
| `DOUGHNUT` | 展示占比（环形） | 同饼图 |

#### 常见问题

1. **占位符没有被替换，显示 `[图表未生成: xxx]`**  
   表示该 SQL 没有成功生成图表。请检查：
   - SQL 是否开启了「生成图表」。
   - SQL 查询结果是否为空。
   - 结果中是否存在可用的数值列；饼图/环形图需要至少 1 个分类列和 1 个数值列。

2. **邮件中收到图片但企业微信没有**  
   企业微信渠道会先发文本消息，再单独发送图片消息。请确认企业微信配置的图片上传权限正常。

3. **模板链中的 SQL 没有生成图表**  
   当前版本图表生成仅对未绑定模板的单 SQL 生效。绑定到 Word / PPT 模板链的 SQL 暂不生成图表。

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
- 定时任务使用 Quartz JDBC JobStore，任务状态持久化到数据库。
- 邮件正文支持 HTML，可直接在 `body` 中编写 `<p>`、`<table>` 等标签。