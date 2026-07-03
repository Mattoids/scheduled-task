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

## SQL 模块

一个任务可以关联一条或多条 SQL。每条 SQL 独立配置数据源、输出格式、文件名规则与模板。

### SQL 分组

SQL 配置支持 `group_name` 分组：

- 在 SQL 管理中填写分组名称，如 `门店报表`、`财务日报`
- SQL 管理列表支持按分组筛选
- 任务配置中选择 SQL 时，下拉框会按分组展示，便于在 SQL 较多时快速定位
- 未填写分组的 SQL 会归入“未分组”

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
- 任务的 **邮件主题**（`emailSubject`）
- 任务的 **邮件正文**（`emailBody`）

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

## 注意事项

- 项目使用简单 AES 加密存储密码，生产环境建议更换密钥或使用 KMS。
- 定时任务使用 Quartz RAMJobStore，重启后会从数据库重新加载所有启用状态的任务。
- 邮件正文支持 HTML，可直接在 `emailBody` 中编写 `<p>`、`<table>` 等标签。
