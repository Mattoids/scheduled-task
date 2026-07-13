# 企业微信应用可信 IP 自动同步 — 开发手册

## 一、背景与原理

企业微信的「企业可信 IP」白名单**没有公开 OpenAPI**，只能通过管理后台（`work.weixin.qq.com`）网页端操作。MoviePilot 的 dynamicwechat 插件通过 Playwright 浏览器自动化实现了扫码登录 → Cookie 保存 → DOM 操作修改可信 IP 的完整流程。

本项目（Java + Spring Boot）使用 **Selenium 4 + ChromeDriver** 复刻同一流程。

## 二、核心流程

### 2.1 扫码登录获取 Cookie

```
用户点击「自动获取Cookie」按钮
    │
    ▼
后端启动 ChromeDriver (headless)
    │
    ▼
访问 https://work.weixin.qq.com/wework_admin/loginpage_wx?from=myhome
    │
    ▼
等待 iframe 加载 → 切换到 iframe → 找到 img.qrcode_login_img
    │
    ▼
下载二维码图片 → Base64 编码 → 返回给前端
    │
    ▼
前端弹窗展示二维码，开始轮询登录状态
    │
    ▼
用户用企业微信 App 扫码确认
    │
    ▼
后端检测到页面跳转（登录成功）
    │
    ▼
提取浏览器 Cookie → 格式化为字符串 → 返回给前端
    │
    ▼
前端自动填入 Cookie 输入框，关闭弹窗
```

### 2.2 定时同步可信 IP

```
定时任务触发（默认每 10 分钟，可配置）
    │
    ▼
检测当前公网 IP（多个 IP 检测源轮询）
    │
    ▼
启动 ChromeDriver，注入已保存的 Cookie
    │
    ▼
访问 https://work.weixin.qq.com/wework_admin/frame#apps/modApiApp/{agentId}
    │
    ▼
点击「配置」按钮 → 等待 textarea.js_ipConfig_textarea 出现
    │
    ▼
读取当前可信 IP 列表
    │
    ├── 当前 IP 已在列表中 → 关闭弹窗，跳过本次
    │
    └── 当前 IP 不在列表中：
            │
            ├── 保留第一个 IP
            ├── 替换最后一个 IP 为当前 IP
            └── 确保至少 2 个 IP
            │
            ▼
        填入新 IP 列表 → 点击确认按钮
```

### 2.3 IP 白名单管理策略

| 场景 | 操作 |
|------|------|
| 白名单只有 1 个 IP | 保留该 IP + 追加新 IP（共 2 个） |
| 白名单有 2+ 个 IP，当前 IP 已存在 | 跳过，不做修改 |
| 白名单有 2+ 个 IP，当前 IP 不存在 | 保留第一个 IP，替换最后一个 IP |

## 三、技术实现

### 3.1 公网 IP 检测

从多个 IP 检测服务轮询获取，使用正则提取 IPv4 地址：

```java
private static final List<String> IP_DETECTION_URLS = List.of(
    "https://myip.ipip.net",
    "https://ddns.oray.com/checkip",
    "https://ip.3322.net"
);
private static final Pattern IP_PATTERN =
    Pattern.compile("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b");
```

### 3.2 ChromeDriver 反检测配置

企业微信会检测无头浏览器，需要添加 stealth 参数：

```java
ChromeOptions options = new ChromeOptions();
options.addArguments("--headless=new");
options.addArguments("--no-sandbox");
options.addArguments("--disable-dev-shm-usage");
options.addArguments("--disable-blink-features=AutomationControlled");
options.addArguments("--lang=zh-CN");
options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
options.setExperimentalOption("useAutomationExtension", false);
```

### 3.3 关键 DOM 选择器

| 元素 | 选择器 |
|------|--------|
| 登录页 iframe | `iframe` |
| 二维码图片 | `img.qrcode_login_img`（iframe 内） |
| 配置按钮 | `//div[contains(@class,'js_show_ipConfig_dialog')]//a[text()='配置']` |
| IP 输入框 | `textarea.js_ipConfig_textarea` |
| 确认按钮 | `.js_ipConfig_confirmBtn` |

### 3.4 Cookie 管理

- Cookie 格式：`key1=value1; key2=value2; ...`（标准 HTTP Cookie 头格式）
- 存储：保存在 `NotificationConfig.configJson.adminCookie` 字段中，AES 加密
- 注入：启动浏览器后通过 `driver.manage().addCookie()` 逐个注入

### 3.5 配置字段（configJson 新增）

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `autoSyncIp` | Boolean | false | 是否启用自动 IP 同步 |
| `adminCookie` | String | "" | 企业微信管理后台 Cookie（加密存储） |
| `syncIntervalMinutes` | Integer | 10 | 同步间隔（分钟） |

## 四、API 接口

### 4.1 生成登录二维码

```
POST /api/wecom-ip-sync/qr-code
Response: { sessionId: "uuid", qrCodeBase64: "data:image/png;base64,..." }
```

### 4.2 轮询登录状态

```
GET /api/wecom-ip-sync/login-status/{sessionId}
Response: { status: "WAITING" | "LOGGED_IN" | "EXPIRED", cookie?: "..." }
```

### 4.3 手动触发同步

```
POST /api/wecom-ip-sync/trigger/{configId}
Response: { success: true, message: "..." }
```

## 五、前端交互

### 5.1 通知配置弹窗新增字段（WECOM_APP 类型）

```
┌─────────────────────────────────────────────┐
│  [开关] 自动同步可信 IP                        │
│                                              │
│  （开关开启后显示以下内容）                        │
│                                              │
│  企业微信 Cookie:                              │
│  [________________________________]          │
│  [🔍 自动获取 Cookie]                         │
│                                              │
│  同步间隔（分钟）: [10]                         │
└─────────────────────────────────────────────┘
```

### 5.2 二维码弹窗

```
┌──────────────────────────┐
│     扫码登录企业微信        │
│                          │
│    ┌──────────────┐      │
│    │              │      │
│    │  [二维码图片]  │      │
│    │              │      │
│    └──────────────┘      │
│                          │
│  请使用企业微信 App 扫码    │
│  等待中... / 登录成功！     │
└──────────────────────────┘
```

## 六、安全考虑

1. `adminCookie` 使用 AES 加密存储在数据库中（`ENC(...)` 格式）
2. QR 码登录会话 3 分钟超时自动清理
3. ChromeDriver 实例在使用完毕或超时后立即 `quit()`
4. 定时任务使用 `@Scheduled` 单线程执行，避免并发冲突

## 七、依赖

项目已有 Selenium 4.21 依赖（`pom.xml`），无需额外引入。

## 八、文件变更清单

### 后端（新增 3 个文件，修改 2 个文件）

| 文件 | 操作 | 说明 |
|------|------|------|
| `entity/WeComAppConfig.java` | 修改 | 新增 autoSyncIp/adminCookie/syncIntervalMinutes |
| `service/wecom/WeComIpSyncService.java` | 新增 | 核心 IP 同步服务 |
| `controller/WeComIpSyncController.java` | 新增 | REST API 控制器 |
| `service/wecom/WeComIpSyncScheduler.java` | 新增 | 定时调度 |
| `service/NotificationConfigService.java` | 修改 | 加解密 adminCookie |

### 前端（新增 1 个文件，修改 2 个文件）

| 文件 | 操作 | 说明 |
|------|------|------|
| `types/entity.ts` | 修改 | WeComAppConfig 接口增加字段 |
| `api/wecomIpSync.ts` | 新增 | API 封装 |
| `views/notification-config/NotificationConfigForm.vue` | 修改 | 新增开关/输入框/二维码弹窗 |
