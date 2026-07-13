# 定时任务报表系统

基于 Spring Boot 3 + MyBatis-Plus + Quartz + Spring Security + JWT 的定时任务报表系统，支持 SQL 任务、网页爬取任务、多数据源、SSH 隧道、图表生成、通知推送、RBAC 权限控制等能力。

## 核心功能

- **任务调度**：支持 CRON 周期任务与一次性任务，基于 Quartz JDBC JobStore 持久化
- **SQL 任务**：多数据源 SQL 查询，输出 CSV / Excel / Word / PPT / TXT / INLINE
- **网页爬取任务**：支持静态 / 动态页面爬取、CSS/XPath/Regex 数据提取、分页、媒体下载
- **模板处理**：Excel / Word / PPT / CSV / TXT 模板，支持 `${字段名}` 占位符与多 SQL 链式填充
- **图表生成**：柱状图、折线图、饼图、面积图、散点图、堆叠柱状图、环形图
- **通知系统**：邮件、企业微信应用 / 群机器人 / 智能机器人、钉钉、飞书、Slack、自定义 Webhook
- **AI 助手**：通知内容优化、自然语言意图解析、企业微信闲聊回复
- **存储配置**：本地、阿里云 OSS、AWS S3、WebDAV
- **系统管理**：RBAC 用户 / 角色 / 权限、操作审计日志
- **数据源 SSH 隧道**：数据库连接与爬取任务均支持 SSH 隧道及跳板机

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
- Jsoup / Selenium（网页爬取）

## 快速开始

### 1. 初始化数据库

项目使用 Flyway 自动管理数据库迁移，首次启动时会自动执行 `src/main/resources/db/migration` 下的脚本，无需手动导入 schema.sql。

如需手动初始化，可按版本号顺序执行该目录下的迁移脚本。

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
java -jar target/scheduled-task-*.jar --spring.profiles.active=dev
```

### 4. 默认账号

- 用户名：`admin`
- 密码：`admin123`

登录接口：`POST /api/auth/login`

---

## 任务管理

### 任务调度

任务统一由 `task_config` 管理，核心字段包括：

| 字段 | 说明 |
|------|------|
| `taskName` | 任务名称 |
| `taskCode` | 任务编码，作为业务主键关联 SQL、爬取配置、通知规则等 |
| `triggerType` | `CRON` / `ONCE` |
| `triggerConfig` | CRON 表达式或一次性执行时间 |
| `status` | `ENABLE` / `DISABLE` |
| `taskType` | `SQL` / `CRAWL` |

- 支持**手动触发**和**外部 API 触发**（`POST /api/public/tasks/{taskId}/trigger`，需 `X-API-KEY`）
- 任务状态变更后会自动重新调度或移除 Quartz 触发器
- **并发控制**：同一任务在 1 小时内已有 `RUNNING` 日志时，新触发会被标记为 `SKIPPED`

### 任务类型

- **SQL 任务**：关联一条或多条 `task_sql_config`，按 `sort_order` 顺序执行
- **网页爬取任务**：关联一条 `task_web_crawl_config`，抓取网页并提取数据

### 任务依赖

支持为任务配置上游依赖（`task_dependency`）：

- 保存时自动检测并拒绝循环依赖
- 手动触发时按拓扑顺序执行依赖链，任一依赖失败则中止
- 任务成功后自动级联触发下游依赖任务
- 提供依赖链查询接口 `/api/task/{id}/dependency-chain`

---

## SQL 任务

### 基础配置

每条 SQL 独立配置：

| 配置项 | 说明 |
|--------|------|
| `datasourceId` | 数据源 ID |
| `sqlContent` | SQL 语句，支持 `${参数名}` 占位符 |
| `customParams` | 自定义参数 JSON |
| `outputFormat` | `CSV` / `EXCEL` / `WORD` / `PPT` / `TXT` / `INLINE` |
| `fileNamePattern` | 输出文件名，支持时间占位符 |
| `templateCode` | 关联的报表模板编码 |
| `groupCode` | SQL 分组编码 |

### SQL 变量与参数

SQL 中 `${参数名}` 按以下顺序替换：

1. `customParams` 自定义参数
2. 内置日期/时间变量

自定义参数示例：

```json
{
  "startTime": "2026-07-01 00:00:00",
  "endTime": "2026-07-01 23:59:59",
  "city": "北京"
}
```

支持格式后缀：`${startTime:yyyy-MM-dd}`。

### 输出格式

| 格式 | 说明 |
|------|------|
| `CSV` | 默认格式，使用 SQL 列别名作为表头 |
| `EXCEL` | 生成 `.xlsx`，支持 `_sheet_name` 列分 sheet、合并组、追加模式、循环生成 |
| `TXT` | 按模板中 `${字段名}` 逐行渲染 |
| `WORD` / `PPT` | 无模板时自动回退为 CSV |
| `INLINE` | 不生成文件，直接将 SQL 结果嵌入通知内容 |

### Excel 高级特性

#### 1. 单 SQL 分 sheet

SQL 结果中包含 `_sheet_name` 列时，按该列值拆分为多个 sheet，该列不写入单元格。

#### 2. 多 SQL 合并输出

通过 `excelMergeGroup` 和 `excelSheetName` 控制：

- 相同 `excelMergeGroup` 的 SQL 输出到同一个 Excel 文件
- 相同 `excelSheetName` 的 SQL 追加到同一个 sheet
- 不同 `excelSheetName` 创建不同 sheet

#### 3. Excel 循环生成

开启 `excelLoopEnabled` 并配置 `excelLoopConfig`，可按 `MONTH` / `DAY` / `WEEK` / `YEAR` / `HOUR` / `MINUTE` 循环执行 SQL，每次循环结果作为独立 sheet 输出。

#### 4. Excel 追加模式

开启 `excelAppendMode` 并配置 `excelBaseFilePath`：

- 向已有 Excel 文件追加新 sheet
- `excelAppendUpdateSameSheet`：同名 sheet 更新（1）或跳过（0）
- `excelAppendPosition`：新 sheet 插入位置，从 0 开始；留空或负数表示追加到末尾

#### 5. Excel 模板特性

- 支持**显示表头 + 占位符行**或**纯字段名表头**
- 同一 sheet 内多个不相邻的 `${}` 列组会被识别为独立数据区域
- 支持**自动汇总行**：包含 `SUM(单单元格)` 的公式行会自动下移到数据末尾，并扩展为 `SUM(首行:末行)`
- 新增行复制样例数据行样式
- 支持 `${chart}` / `${chart:sql编码}` 插入图表图片

### 多 SQL 链式处理（模板链）

多条 SQL 绑定到同一个模板时，按顺序依次填充：

1. 第 1 条 SQL 填充模板，生成临时文件
2. 第 2 条 SQL 继续填充上一步的临时文件
3. ...
4. 最后一条 SQL 生成最终报表文件

该机制常用于 Word / PPT / Excel 中先替换基础信息，再展开明细表格或汇总区域。

---

## 网页爬取任务

### 请求配置

| 配置项 | 说明 |
|--------|------|
| `requestUrl` | 请求 URL，支持占位符 |
| `requestMethod` | GET / POST / PUT / DELETE 等 |
| `requestHeaders` | 请求头 JSON |
| `requestParams` | URL 参数 JSON |
| `requestBody` | 请求体 |
| `requestContentType` | 内容类型 |
| `cookies` | Cookie 字符串 |
| `authType` | `NONE` / `BASIC` / `TOKEN` / `FORM` / `OAUTH2` |
| `authConfig` | 认证配置 JSON |

### 数据提取规则

通过 `selectors` 配置：

| 配置项 | 说明 |
|--------|------|
| `fieldName` | 字段名 |
| `selectorType` | `CSS` / `XPATH` / `REGEX` |
| `selectorValue` | 选择器表达式 |
| `attribute` | 取值属性：`text` / `html` / `src` / `href` / `attr:xxx` |
| `dataType` | `STRING` / `NUMBER` / `DATE` / `LINK` / `HTML` |
| `isRowSelector` | 是否为行级选择器，决定数据行粒度 |
| `defaultValue` | 默认值 |

### 渲染方式

- **STATIC**：使用 Jsoup 直接请求静态页面
- **DYNAMIC**：基于 Selenium ChromeDriver 渲染动态页面，可配置窗口大小、等待选择器、浏览器路径、启动参数等

### 分页爬取

- `URL_TEMPLATE`：按模板生成下一页 URL
- `SELECTOR`：通过 CSS 选择器提取下一页链接
- 支持最大页数限制，`<=0` 表示不限制

### 媒体下载

开启 `mediaEnabled` 后可抓取图片 / 视频 / 音频等资源：

- 支持文件类型、尺寸、大小、MIME 类型过滤
- 输出模式：`FILES` / `ZIP` / `STORE_ONLY` / `ATTACH_ZIP`
- 可打包为 ZIP 并上传到存储配置

### 网络代理与 SSH 隧道

- **HTTP 代理**：`proxyEnabled` + host/port/username/password，支持 Jsoup 和 ChromeDriver
- **SSH 隧道**：支持密码或私钥登录，支持跳板机模式，自动将请求 URL 替换为 `127.0.0.1:localPort`

### 预览接口

- `POST /api/task-crawl/preview`：返回页面标题和 HTML
- `POST /api/task-crawl/preview-rewrite`：重写页面资源链接以便 iframe 预览
- `GET /api/task-crawl/preview-resource`：代理预览子资源（CSS/JS/图片）

---

## 模板说明

模板中使用 `${字段名}` 作为占位符，系统会用 SQL / 爬取结果进行替换或展开。字段名区分大小写，需与列名/列别名完全一致。

### 通用占位符

| 占位符 | 说明 |
|--------|------|
| `${字段名}` | SQL / 爬取结果中的字段值 |
| `${序号}` / `${seq}` | 表格展开时自动生成从 1 开始的序号 |
| `${chart}` | 插入当前 SQL 生成的图表 |
| `${chart:sql编码}` | 插入指定 `sql_code` 生成的图表 |

### Excel 模板

- 支持显示表头 + 占位符行，或纯字段名表头
- 同一 sheet 内多个不相邻数据区域独立扩展
- 支持自动汇总行
- 新增行复制样例行样式
- 支持图表占位符

汇总行示例：

| 城市 | 门店 | 打卡次数 |
|------|------|----------|
| `${city_name}` | `${store_name}` | `${checkin_num}` |
| 汇总 | `=SUM(C2)` | `=SUM(C2)` |

当 SQL 返回 3 条数据时，汇总行会自动下移到第 5 行，公式更新为 `=SUM(C2:C4)`。

### Word 模板

- 段落中可直接使用 `${字段名}` 替换单行数据
- 表格中第一行使用 `${字段名}` 作为表头，自动展开数据行
- 支持只有表头没有样例行的表格
- 支持图表占位符

### PPT 模板

- 文本框中可直接使用 `${字段名}` 替换
- 表格展开逻辑与 Word 类似
- 多行数据时才展开表格；单行数据按普通占位符替换
- 支持图表占位符

### CSV / TXT

- CSV：第一行为表头，使用字段名
- TXT：整份文件作为一行模板，每条数据渲染为一行

---

## 图表生成

基于 XChart 生成 PNG 图表，支持的类型：

| 类型 | 说明 |
|------|------|
| `BAR` | 柱状图 |
| `LINE` | 折线图 |
| `AREA` | 面积图 |
| `SCATTER` | 散点图 |
| `STACKED_BAR` | 堆叠柱状图 |
| `PIE` | 饼图 |
| `DOUGHNUT` | 环形图 |

数据识别规则：

- 分类轴优先选择第一个非数值列
- 其余数值列作为 Y 轴数据序列
- 单列数值时自动用行号作为分类轴

配置项：

| 配置项 | 说明 |
|--------|------|
| `chartEnabled` | 是否启用 |
| `chartType` | 图表类型 |
| `chartTitle` | 标题，留空使用 SQL 名称 |
| `chartAutoMerge` | 分类过多时自动合并相邻数据 |
| `chartLabelRotation` | X 轴标签旋转：`AUTO` / `0` / `45` / `90` |
| `chartBackgroundColor` | 背景色。留空默认白色；显式 `transparent` 或 alpha=0 表示透明 |

图表使用方式：

- 邮件正文内联图片：`cid:chart_编码`
- 企业微信：先发送文本，再补发图片消息
- Word / PPT / Excel 模板：`${chart:sql编码}`
- 通知内容：`${chart:sql编码}`

---

## 通知系统

### 通知渠道

| 渠道 | 说明 |
|------|------|
| `EMAIL` | SMTP 邮件，支持 HTML 正文、内联图片、附件 |
| `WECOM_APP` | 企业微信应用消息，支持 Markdown、文件、图片 |
| `WECOM_BOT` | 企业微信群机器人 Webhook |
| `WECOM_INTELLIGENT_BOT` | 企业微信智能机器人，支持 `LONGCHAIN` / `CALLBACK` 模式 |
| `DINGTALK` | 钉钉机器人 Webhook |
| `FEISHU` | 飞书机器人 Webhook |
| `SLACK` | Slack Incoming Webhook |
| `WEBHOOK` | 自定义 HTTP Webhook |

### 通知规则

- 触发事件：`TASK_SUCCESS` / `TASK_FAILURE` / `TASK_COMPLETED`
- 可全局生效或绑定指定 `task_code`
- 支持邮件收件人 / 收件组、企业微信 `@用户`
- 支持 AI 优化通知内容
- 支持指定存储配置，将文件转为下载链接发送
- 发送失败时最多重试 2 次
- 通知日志 `notification_log` 记录发送状态、接收人、失败原因

### 通知模板占位符

邮件主题、正文、企业微信文本模板均支持：

- `${变量名}` 自定义参数和内置变量
- `${chart:sql编码}` 插入图表
- 内联 SQL 结果：单行单列直接使用列名；多行或多列使用列名作为数组变量

### 企业微信指令

CALLBACK 模式下支持消息指令：

| 指令 | 说明 |
|------|------|
| `帮助` | 显示可用指令 |
| `任务列表 [页码]` | 查看任务列表 |
| `查看任务 {ID}` | 查看任务详情 |
| `任务日志 {ID} [页码]` | 查看任务日志 |
| `最近任务` | 查看最近执行记录 |
| `运行 {ID}` / `运行 {任务名} [时间范围]` | 触发任务 |
| `查询 {关键词} [图表类型]` | 直接查询 SQL 数据 |
| `创建任务 任务名|编码|CRON|sql编码` | 创建简单任务 |

非指令消息会走 AI 意图解析或闲聊回复。

---

## 存储配置

支持多种文件存储后端：

| 类型 | 说明 |
|------|------|
| `LOCAL` | 本地文件系统 |
| `OSS` | 阿里云 OSS |
| `S3` | AWS S3 及兼容存储 |
| `WEBDAV` | WebDAV 服务器 |

用途：

- 企业微信 / 钉钉 / 飞书大文件转链接发送
- 爬取任务媒体文件上传
- 报表文件长期存储

本地文件通过 `/storage/**` 提供访问，并做目录穿越防护。

---

## AI 配置与助手

### AI 配置

支持的厂商：

| 厂商 | 说明 |
|------|------|
| `OPENAI` | OpenAI API |
| `ANTHROPIC` | Anthropic Claude API |
| `AZURE_OPENAI` | Azure OpenAI Service |
| `OLLAMA` | 本地 Ollama 部署 |
| `CUSTOM` | 自定义兼容 OpenAI 格式的 API |

字段：API Key、Base URL、模型、temperature、max_tokens、timeout、系统提示词、默认配置标记。

### AI 助手

- **通知内容优化**：优化邮件/企业微信标题和正文，保留占位符
- **意图解析**：将自然语言解析为 `VIEW_TASKS` / `TRIGGER_TASK` / `VIEW_LOGS` / `CREATE_TASK` / `QUERY_DATA`
- **查询参数提取**：从自然语言中提取业务过滤参数
- **闲聊回复**：企业微信非指令消息友好回复

---

## 系统管理

### RBAC 权限模型

- 用户 `sys_user`、角色 `sys_role`、权限 `sys_permission`
- 用户-角色、角色-权限多对多关联
- 启动时自动初始化默认角色 `ADMIN` / `OPERATOR` / `VIEWER` 和默认管理员 `admin / admin123`
- 后续启动会自动为 `ADMIN` 角色补齐新增权限

主要权限点：

| 权限 | 说明 |
|------|------|
| `task:view/create/edit/delete/trigger` | 任务管理 |
| `taskCrawl:view/create/edit/delete` | 爬取配置 |
| `taskSqlGroup:view/create/edit/delete` | SQL 分组 |
| `datasource:view/create/edit/delete` | 数据源 |
| `email:view/create/edit/delete` | 收件人/收件组 |
| `template:view/create/edit/delete` | 报表模板 |
| `notificationRule:view/create/edit/delete` | 通知规则 |
| `notificationConfig:view/create/edit/delete` | 通知配置 |
| `storageConfig:view/create/edit/delete` | 存储配置 |
| `system:user` / `system:role` | 用户 / 角色权限管理 |
| `log:view` / `auditLog:view` | 任务日志 / 审计日志 |

### 操作审计日志

通过 AOP 切面 `@OperationAudit` 自动记录增删改、执行等操作：

- 记录操作人、操作类型、资源类型、资源 ID/名称、请求 URI/方法、请求参数、IP 地址、状态、错误信息
- 对 `password`、`secret`、`token`、`privatekey`、`passphrase`、`aeskey` 等敏感 key 自动脱敏
- 提供审计日志分页查询接口 `/api/audit-log/page`

---

## 变量与占位符规则

以下配置项支持使用时间变量：

- 任务级 / SQL 级文件名格式
- 通知规则中的邮件主题 / 正文、企业微信文本模板
- SQL 语句、请求 URL、请求体、自定义参数
- 爬取文件名 / ZIP 文件名

### 内置变量

支持两种写法：`{变量名}` 和 `${变量名}`，均支持格式后缀。

| 变量 | 说明 | 默认格式 | 示例（2026-07-04） |
|------|------|---------|-------------------|
| `{month}` / `{currentMonth}` | 当前月份 | `M` | `7` |
| `{lastMonth}` | 上月月份 | `MM` | `06` |
| `{nextMonth}` | 下月月份 | `yyyyMM` | `202608` |
| `{lastM}` / `{nextM}` | 上月/下月数字 | `M` | `6` / `8` |
| `{year}` / `{currentYear}` | 当前年份 | `yyyy` | `2026` |
| `{lastYear}` / `{nextYear}` | 去年 / 明年 | `yyyy` | `2025` / `2027` |
| `{now}` / `{date}` / `{today}` | 当前日期 | `yyyy-MM-dd` | `2026-07-04` |
| `{yesterday}` / `{tomorrow}` | 昨天 / 明天 | `yyyy-MM-dd` | `2026-07-03` / `2026-07-05` |
| `{firstDayOfThisWeek}` | 本周第一天（周一） | `yyyy-MM-dd` | `2026-06-30` |
| `{lastDayOfThisWeek}` | 本周最后一天（周日） | `yyyy-MM-dd` | `2026-07-06` |
| `{firstDayOfLastWeek}` | 上周第一天 | `yyyy-MM-dd` | `2026-06-23` |
| `{lastDayOfLastWeek}` | 上周最后一天 | `yyyy-MM-dd` | `2026-06-29` |
| `{firstDayOfThisMonth}` | 本月第一天 | `yyyy-MM-dd` | `2026-07-01` |
| `{lastDayOfThisMonth}` | 本月最后一天 | `yyyy-MM-dd` | `2026-07-31` |
| `{firstDayOfLastMonth}` | 上月第一天 | `yyyy-MM-dd` | `2026-06-01` |
| `{lastDayOfLastMonth}` | 上月最后一天 | `yyyy-MM-dd` | `2026-06-30` |
| `{firstDayOfThisQuarter}` | 本季度第一天 | `yyyy-MM-dd` | `2026-07-01` |
| `{lastDayOfThisQuarter}` | 本季度最后一天 | `yyyy-MM-dd` | `2026-09-30` |
| `{firstDayOfLastQuarter}` | 上季度第一天 | `yyyy-MM-dd` | `2026-04-01` |
| `{lastDayOfLastQuarter}` | 上季度最后一天 | `yyyy-MM-dd` | `2026-06-30` |
| `{firstDayOfThisYear}` | 今年第一天 | `yyyy-MM-dd` | `2026-01-01` |
| `{lastDayOfThisYear}` | 今年最后一天 | `yyyy-MM-dd` | `2026-12-31` |
| `{firstDayOfLastYear}` | 去年第一天 | `yyyy-MM-dd` | `2025-01-01` |
| `{lastDayOfLastYear}` | 去年最后一天 | `yyyy-MM-dd` | `2025-12-31` |
| `{firstDayOfNextYear}` | 明年第一天 | `yyyy-MM-dd` | `2027-01-01` |
| `{lastDayOfNextYear}` | 明年最后一天 | `yyyy-MM-dd` | `2027-12-31` |
| `{yyyyMMddHHmmss}` | 当前时间 | - | `20260704123045` |
| `{yyyyMMdd}` / `{yyyy-MM-dd}` | 当前日期 | - | `20260704` / `2026-07-04` |
| `{HHmmss}` | 当前时间 | - | `123045` |
| `{yyyy}` / `{MM}` / `{dd}` 等 | Java `DateTimeFormatter` 支持的任意格式 | - | - |

### 文件名示例

```
report_{yyyyMMddHHmmss}.csv
{lastMonth}月门店打卡报表_{yyyyMMdd}.xlsx
```

---

## 数据源与 SSH 隧道

### 多数据源

支持数据库类型：MySQL、PostgreSQL、Oracle、SQLServer。

字段包括：名称、类型、主机、端口、库名、用户名、密码、驱动类、JDBC 参数。

支持测试连接，动态创建/刷新 HikariCP 连接池。

### SSH 隧道

- 数据源可开启 `sshEnabled`，通过 SSH 隧道访问目标数据库
- 支持密码或私钥 + 可选密码短语认证
- 建立本地端口转发，数据库连接指向 `127.0.0.1:sshLocalPort`
- 密码、私钥等敏感字段加密存储

---

## 主要接口

| 功能 | 接口 |
|------|------|
| 登录 | `POST /api/auth/login` |
| 当前用户 | `GET /api/auth/me` |
| 修改密码 | `POST /api/auth/change-password` |
| 仪表盘统计 | `GET /api/dashboard/stats` |
| 执行趋势 | `GET /api/dashboard/execution-trend` |
| 任务分页 | `GET /api/task/page` |
| 创建任务 | `POST /api/task` |
| 修改任务 | `PUT /api/task/{id}` |
| 更新任务状态 | `PUT /api/task/{id}/status` |
| 手动触发任务 | `POST /api/task/{id}/trigger` |
| 依赖链 | `GET /api/task/{id}/dependency-chain` |
| 外部触发任务 | `POST /api/public/tasks/{taskId}/trigger` |
| 任务日志（按任务） | `GET /api/task/{taskId}/logs` |
| 任务日志分页 | `GET /api/task-log/page` |
| SQL 配置分页 | `GET /api/task-sql/page` |
| SQL 配置列表 | `GET /api/task-sql/list` |
| SQL 分组分页 | `GET /api/task-sql-group/page` |
| SQL 分组列表 | `GET /api/task-sql-group/list` |
| 爬取配置分页 | `GET /api/task-crawl/page` |
| 爬取预览 | `POST /api/task-crawl/preview` |
| 数据源分页 | `GET /api/datasource/page` |
| 测试数据源 | `POST /api/datasource/{id}/test` |
| 收件人分页 | `GET /api/email-recipient/page` |
| 收件人列表 | `GET /api/email-recipient/list` |
| 收件人群组列表 | `GET /api/email-recipient/group/list` |
| 模板分页 | `GET /api/template/page` |
| 模板上传 | `POST /api/template/upload` |
| 删除模板 | `DELETE /api/template/{id}` |
| 通知配置分页 | `GET /api/notification-config/page` |
| 测试通知配置 | `POST /api/notification-config/test` |
| 通知规则分页 | `GET /api/notification-rule/page` |
| 通知规则列表 | `GET /api/notification-rule/list` |
| AI 配置分页 | `GET /api/ai-config/page` |
| AI 配置列表 | `GET /api/ai-config/list` |
| 测试 AI 配置 | `POST /api/ai-config/test` |
| AI 意图解析 | `POST /api/assistant/parse-intent` |
| AI 优化通知 | `POST /api/assistant/optimize-notification` |
| 存储配置分页 | `GET /api/storage-config/page` |
| 存储配置列表 | `GET /api/storage-config/list` |
| 测试存储配置 | `POST /api/storage-config/{id}/test` |
| 存储文件访问 | `GET /storage/**` |
| 审计日志分页 | `GET /api/audit-log/page` |
| 企业微信回调 | `GET/POST /api/wecom/callback/{configId}` |
| 用户分页 | `GET /api/system/user/page` |
| 角色列表 | `GET /api/system/role/list` |
| 权限列表 | `GET /api/system/permission/list` |

---

## 注意事项

- 项目使用 AES 加密存储密码、私钥、Cookie、Token 等敏感字段，生产环境建议更换密钥或使用 KMS。
- 定时任务使用 Quartz JDBC JobStore，任务状态持久化到数据库。
- 邮件正文支持 HTML，可直接在 `body` 中编写 `<p>`、`<table>` 等标签。
- 外部触发接口 `/api/public/tasks/{taskId}/trigger` 需配置 `report.api.key` 并通过 `X-API-KEY` 请求头校验。
