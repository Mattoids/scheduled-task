# 优化当前Java调度任务项目的安全、性能与架构

基于代码审计，项目存在三类核心问题：安全（默认密钥、ECB加密、SQL拼接、信任所有SSL）、性能（无连接池的RestTemplate、同步阻塞调用、缺失缓存、Thread.sleep等待）、架构（多个god类混合职责、控制器直接调用mapper、前端资源未清理）。优化顺序按P0安全→P1架构解耦与性能→P2可观测与工程化展开，先修复高风险安全缺陷，再拆分核心god类，最后补齐缓存、索引与监控。

## Tasks

- [x] P001: 移除application.yml<path> <!-- scope: src<path> -->
  - Acceptance: 所有secret<path>
- [x] P002: 将CryptoUtil从AES<path> <!-- scope: src<path> -->
  - Acceptance: 新写入数据使用AES-GCM并附带IV；提供一次性迁移脚本将旧ECB密文解密再加密为GCM；单元测试覆盖加密、解密、篡改检测失败场景。
- [x] P003: 将SqlExecutor的${...}字符串替换改为参数化查询 <!-- scope: src<path> -->
  - Acceptance: processSqlVariables不再使用字符串拼接，全部通过PreparedStatement设置参数；SQL注入测试用例全部通过；保留read-only校验但额外使用JDBC setReadOnly(true)连接。
- [x] P004: 清理WebCrawlExecutor中静态信任所有SSL的副作用 <!-- scope: src<path> -->
  - Acceptance: 移除static块中对全局SSLContext和HostnameVerifier的修改；改为每个HttpClient实例可控的trust策略或配置可信任证书；不污染JVM全局HTTPS连接。
- [x] P005: 将NotificationEventListener拆分为NotificationChannel SPI <!-- scope: src<path> -->
  - Acceptance: 定义NotificationChannel接口及统一上下文入参；每个渠道（钉钉、飞书、Slack、Webhook、企业微信、邮件、短信、AI优化）独立实现类；原有监听器仅负责路由与重试，不再包含渠道逻辑。
- [ ] P006: 重构TaskExecutionService为编排器+处理器模式 <!-- scope: src<path> -->
  - Acceptance: 拆分出TaskOrchestrator、SqlTaskHandler、CrawlTaskHandler、ReportAssembler、NotificationDispatcher；TaskExecutionService行数降至300行以内；依赖任务仍串行执行但由编排器显式调度，单测覆盖各handler。
- [ ] P007: 重构WebCrawlExecutor为HttpEngine+SelectorEngine+PaginationStrategy+MediaDownloader <!-- scope: src<path> -->
  - Acceptance: buildConnection<path>
- [ ] P008: 统一RestTemplate<path> client为单个带连接池的Bean <!-- scope: ai<path> -->
  - Acceptance: 删除所有new RestTemplate()，改为注入RestTemplateBuilder构建的共享Bean（Apache HttpClient连接池）；统一超时、重试、错误处理配置；通知客户端复用同一响应解析工具。
- [ ] P009: 修复TaskExecutionService的并发执行竞态条件 <!-- scope: src<path> -->
  - Acceptance: 将内存中的runningTasks检查替换为数据库乐观锁（status+version）或分布式锁；同一任务在集群多实例下不会重复执行；并发测试覆盖双实例同时触发同任务场景。
- [ ] P010: 为高频只读查询添加Spring Cache <!-- scope: AuthController.java、AiConfigService.java、WeComAppManager.java、DynamicDataSourceManager.java -->
  - Acceptance: me()<path> token、schema doc查询使用@Cacheable并配置TTL；配置变更时通过@CacheEvict失效缓存；缓存未命中时数据库查询次数不高于1次。
- [ ] P011: 替换项目中的Thread.sleep为事件<path> <!-- scope: event<path> -->
  - Acceptance: Selenium使用ExpectedCondition、SSH使用forwarder启动信号、重试使用指数退避的ScheduledExecutorService或RetryTemplate；Thread.sleep调用数降为0。
- [ ] P012: 清理前端static<path> <!-- scope: scheduled-task-ui<path> -->
  - Acceptance: 每次vite build使用emptyOutDir清空输出目录；Maven打包前清理src<path>
- [ ] P013: 为task_log等热表添加索引并优化N+1查询 <!-- scope: 数据库迁移脚本、TaskConfigController.java、AuthController.java、EmailRecipientService.java -->
  - Acceptance: 添加task_log(task_id,status,start_time)复合索引；TaskConfigController.detail()使用关联查询一次性加载task<path>
- [ ] P014: 规范实体层，移除@EqualsAndHashCode并拆分DTO字段 <!-- scope: domain<path> -->
  - Acceptance: 所有实体使用@Getter<path>
- [ ] P015: 增强可观测性与安全边界 <!-- scope: GlobalExceptionHandler.java、PublicTaskController.java、跨切面 -->
  - Acceptance: 异常响应包含requestId和traceable错误码；PublicTaskController添加基于Bucket4j或Guava的限流；关键路径（AI调用、通知、存储上传）接入Micrometer计数与超时指标。
- [ ] P016: Final validation <!-- scope: validation -->
  - Acceptance: 1) 运行mvn test全量测试通过；2) 运行mvn spotbugs:check / sonar:sonar无P0<path> blocker；3) grep -R 'new RestTemplate()' src<path> 返回空；4) grep -R 'AES<path>' src<path> 返回空；5) grep -R 'Thread.sleep' src<path> 返回空；6) 启动应用后，未配置必填secret时进程退出并打印明确错误；7) 前端构建后src<path> 并发测试验证同一任务在双实例下只执行一次。
