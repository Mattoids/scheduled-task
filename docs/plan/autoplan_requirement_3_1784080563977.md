# 修复 Chromium<path> 依赖下载安装模块报错

通过代码审查发现：前端 SSE 安装流使用 GET 请求调用后端的 POST 接口导致 405；Spring Boot fat jar 默认使用 JarLauncher，使 -Dloader.main 启动 Playwright CLI 失效；Linux 下仅检测文件存在性会掩盖共享库缺失；安装 key 归一化不一致导致前端 loading 状态错位。本计划统一前后端协议、修正 jar launcher、增强可执行文件检测与状态一致性，并通过测试与 Docker 构建验证。

## Tasks

- [x] P001: 修复前端 SSE 客户端支持 POST 方法 <!-- scope: scheduled-task-ui<path> -->
  - Acceptance: createSse 增加 method 选项且默认保持 GET，installDependency 显式传入 POST；调用 <path> 不再返回 405，SSE 正常接收事件
- [x] P002: 修复 Spring Boot fat jar 中 Playwright CLI 启动方式 <!-- scope: pom.xml、Dockerfile -->
  - Acceptance: 配置 spring-boot-maven-plugin 使用 PropertiesLauncher（如 layout=ZIP 或等效配置），使 java -Dloader.main=com.microsoft.playwright.CLI -jar app.jar install chromium 在 Docker 构建阶段能正确执行
- [x] P003: 修复 Linux 依赖检测仅检查文件存在性的问题 <!-- scope: src<path> -->
  - Acceptance: Linux 环境下即使定位到 Chromium 可执行文件，也执行 ldd 检查缺失共享库；缺失库时返回 installable=true 并列出具体库名
- [x] P004: 修复 Windows 下 Chromium 可执行文件检测 <!-- scope: src<path> -->
  - Acceptance: Windows 平台正确识别 chrome.exe（Files.isExecutable 结合 .exe 后缀或绝对路径存在性），不再因 Files.isExecutable 返回 false 而误判为未安装
- [x] P005: 统一依赖安装 key 归一化与前端加载状态 <!-- scope: scheduled-task-ui<path> -->
  - Acceptance: 缺失系统库项点击安装后，installingKey 与后端归一化 key（chromium）保持一致，按钮 loading 状态正确显示，安装完成后刷新依赖列表；isInstalling 对 lib:* key 也返回正确状态
- [x] P006: 增强安装任务错误与超时处理 <!-- scope: src<path> -->
  - Acceptance: Playwright CLI 返回非零时准确抛出可读的失败原因；30 分钟超时或进程异常时 SSE 返回 error 事件并清理任务，避免任务卡死
- [ ] P007: 为依赖检测与安装服务添加单元测试 <!-- scope: src<path> -->
  - Acceptance: 新增测试覆盖 Linux<path> 路径解析、SSE 任务生命周期、fallback 包管理器选择、安装异常与超时场景，mvn test 通过
- [x] P008: 前端类型检查与构建验证 <!-- scope: scheduled-task-ui/ -->
  - Acceptance: 执行 npm run build（含 vue-tsc -b 类型检查）无错误，成功生成 dist
- [ ] P009: 后端编译与测试验证 <!-- scope: src/ -->
  - Acceptance: 执行 mvn clean compile 无错误；mvn test 通过新增测试
- [x] P010: Docker 镜像构建验证 <!-- scope: Dockerfile、docker-compose.yml -->
  - Acceptance: docker build -t scheduled-task:test . 成功，构建日志显示 Playwright install chromium 与 install-deps 正常完成，镜像内 <path> 存在 chromium-* 目录
- [ ] P011: Final validation <!-- scope: validation -->
  - Acceptance: 1) 启动前后端并登录，进入首页若 Chromium 未安装则点击「安装依赖」，浏览器 Network 中 SSE 请求为 POST 且状态 200，弹窗依次收到 phase<path> 事件；2) 安装完成后刷新页面，系统环境卡片显示「Chromium 内核已安装」；3) 在 Linux 环境手动移除一个共享库后调用 GET <path> installable=true；4) 执行 docker build -t scheduled-task:test . 并通过 docker run --rm scheduled-task:test ls <path> 验证 chromium 已预装
