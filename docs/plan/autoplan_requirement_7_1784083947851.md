# 完善项目未完成功能与质量缺口

基于代码库现状，项目存在三类主要缺口：当前进行中的 Chromium 依赖检测功能尚未完成测试与验证；角色权限、依赖安装提示、审计日志等前后端功能存在未打通或逻辑错误；配置与代码质量方面存在硬编码密钥、空异常处理、缺失校验和死代码。本计划按优先级列出可落地的完善项，先收尾当前特性，再补齐功能闭环，最后治理配置与质量。

## Tasks

- [ ] P001: 完成 Chromium 依赖检测特性的测试与构建验证 <!-- scope: src<path> docs<path> pom.xml, Dockerfile, docker-compose.yml -->
  - Acceptance: 未提交的 DependencyCheckServiceTest、DependencyInstallServiceTest、PlaywrightBrowserLocatorTest 通过；npm run build<path> 无类型错误；mvn clean test 通过；docker build 成功；P007-P011 全部勾选。
- [ ] P002: 修复角色权限管理后端接口缺失 <!-- scope: SystemController.java, SysRole 相关实体<path> 数据库表 -->
  - Acceptance: SystemController 提供 GET <path> 返回全部权限；提供创建<path>
- [ ] P003: 修复角色管理前端权限选择未提交的问题 <!-- scope: scheduled-task-ui<path> scheduled-task-ui<path> -->
  - Acceptance: RoleList.vue 的 handleSubmit 将 selectedPermissions 随角色表单提交；提交前<path>
- [ ] P004: 修复依赖安装 UI 对 lib:* 项不可安装的误导 <!-- scope: DependencyCheckService.java, DashboardView.vue, app.ts -->
  - Acceptance: 缺失系统共享库(lib:*)在 Dashboard 上显示正确安装引导；或 dependenciesReady 计算逻辑不把不可安装的 lib:* 缺失作为阻塞；安装按钮<path> installable 标志一致，用户不再看到矛盾信息。
- [ ] P005: 移除配置文件中的硬编码密钥与默认密码 <!-- scope: application.yml, application-dev.yml, application-test.yml, application-prod.yml, flyway.conf, Dockerfile, docker-compose.yml -->
  - Acceptance: 所有 AES key、JWT secret、API key、DB 密码改为 ${ENV:} 注入且不提供默认值；启动时缺少关键配置直接失败并给出明确错误。
- [ ] P006: 补齐全局异常处理器中缺失的常见异常类型 <!-- scope: GlobalExceptionHandler.java -->
  - Acceptance: 新增 HttpMessageNotReadableException、MissingServletRequestParameterException、MethodArgumentTypeMismatchException、DataIntegrityViolationException 的处理器，返回结构化错误信息和对应 HTTP 状态码。
- [ ] P007: 为系统管理接口补齐输入校验与唯一性检查 <!-- scope: SystemController.java, AuthController.java, ChangePasswordRequest.java -->
  - Acceptance: createUser 校验用户名唯一、密码非空；updateUser 校验旧密码或禁止修改他人密码；createRole<path> 校验 roleCode<path> 非空；changePassword 校验密码复杂度。
- [ ] P008: 修复任务与通知配置的创建<path> <!-- scope: TaskConfigController.java, NotificationConfigController.java, 相关实体 -->
  - Acceptance: create 校验 taskCode 唯一、cron 表达式有效；通知配置校验 configType 合法；避免空密码或重复 key 直接落库。
- [ ] P009: 补齐仪表盘执行趋势前端 API 与视图 <!-- scope: DashboardController.java, scheduled-task-ui<path> DashboardView.vue -->
  - Acceptance: dashboard.ts 新增 execution-trend / execution-trend-chart 调用；DashboardView.vue 展示对应图表或列表；无 404 调用。
- [ ] P010: 补齐 AI 助手意图解析与通知优化前端入口 <!-- scope: AssistantController.java, scheduled-task-ui<path> scheduled-task-ui<path> -->
  - Acceptance: AiChatView.vue 提供使用 parse-intent 或 optimize-notification 的入口；前端 API 文件包含对应方法；调用结果正确展示。
- [ ] P011: 补齐审计日志菜单路由与页面 <!-- scope: scheduled-task-ui<path> OperationAuditLogController.java, 新建 AuditLogList.vue -->
  - Acceptance: routes.ts 注册 <path> 路由；左侧菜单出现审计日志入口；页面可分页调用 GET <path> 并展示数据。
- [ ] P012: 清理死代码与未使用的前端 API<path> <!-- scope: scheduled-task-ui<path> scheduled-task-ui<path> SpaController.java, scheduled-task-ui<path> -->
  - Acceptance: 删除未导入的 checkChromium() 和 previewRewriteTaskCrawl()；移除 routes.ts 与 SPA 转发中不存在的 <path> API 如无 UI 需求则标记弃用或补齐页面。
- [ ] P013: 修复 WeCom 服务中大量空 catch 导致的静默失败 <!-- scope: WeComIpSyncService.java, OpenAiCompatibleClient.java, DependencyInstallService.java -->
  - Acceptance: WeComIpSyncService 中所有 catch(Exception ignored) 至少记录 error 级别日志并含上下文；OpenAiCompatibleClient sleep  helper 按规范处理中断；SSE 发送失败记录 warn 及以上。
- [ ] P014: 收敛 AI 与任务生成中的硬编码默认值 <!-- scope: AiAutoConfigService.java, WeComCommandHandler.java, AiClientFactory.java, SecurityConfig.java -->
  - Acceptance: AI 生成任务的默认 code<path> 等可配置；提供商 base URL 可配置；CORS 允许的 origin 从配置读取；不再出现写死的 localhost:5173 或固定 taskCode 前缀。
- [ ] P015: 重构 NotificationRuleController.update 的字段枚举更新 <!-- scope: NotificationRuleController.java, NotificationRuleService.java -->
  - Acceptance: update 使用实体 copy 或全字段 updateById，新增字段无需修改控制器代码；已有功能保持兼容。
- [ ] P016: 将 WeCom 代理域名白名单改为可配置 <!-- scope: WeComAdminProxyController.java, application.yml -->
  - Acceptance: DOMAIN_MAP、HOST_TO_KEY、BLOCKED_HOSTS 从配置读取；新增<path>
- [ ] P017: 修复 BrowserCapabilityService.refreshChromiumStatus 未使用或补齐调用点 <!-- scope: BrowserCapabilityService.java, 系统启动<path> -->
  - Acceptance: 明确 refreshChromiumStatus 的调用方：如无需则删除；如需刷新能力状态，则在应用启动或检测到 chromium 安装成功后调用。
- [ ] P018: 检查并清理 docker-compose 中冗余的 schema.sql 挂载 <!-- scope: docker-compose.yml, src<path> -->
  - Acceptance: 确认 Flyway 已管理 schema 后，移除 docker-compose 对 schema.sql 的 volume 挂载；如仍需要 init script，确保 schema.sql 与 migration 一致。
- [ ] P019: 补齐 dashboard 与 assistant API 的类型定义并修复类型错误 <!-- scope: scheduled-task-ui<path> scheduled-task-ui<path> 相关视图组件 -->
  - Acceptance: 新增接口返回 TypeScript 类型；npm run type-check 通过。
- [ ] P020: 汇总验证：全链路构建、测试与启动 <!-- scope: 整个仓库 -->
  - Acceptance: mvn clean package 成功；npm run build 成功；后端可启动且日志无缺失关键配置异常；前端可正常访问；依赖检测、角色权限、审计日志、仪表盘趋势、AI 助手功能均通过手工或自动化验证。
- [ ] P021: Final validation <!-- scope: validation -->
  - Acceptance: mvn clean test && mvn clean package -DskipTests=false; cd scheduled-task-ui && npm run build && npm run type-check; docker build -t scheduled-task:validate . ; 启动后端后调用 GET <path> <path> permissions）、GET <path> <path>
