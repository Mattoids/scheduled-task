# 依赖安装模块 UI 与进度持久化改造

将依赖安装进度从弹窗迁移到页面内联显示，限制检测窗口高度并仅展示当前安装项进度；后端新增安装快照持久化与可查询接口，支持 SSE 重连，使刷新页面后可恢复进度；前端新增独立的日志弹窗按钮与自动恢复逻辑。

## Tasks

- [x] P001: 后端：新增安装进度快照模型与存储 <!-- scope: src<path> -->
  - Acceptance: 新增 InstallProgressSnapshot 记录（含 key<path> ConcurrentHashMap<String, InstallProgressSnapshot> installSnapshots；mvn compile 通过。
- [x] P002: 后端：在安装生命周期中持久化进度与日志 <!-- scope: src<path> -->
  - Acceptance: install、doInstall、execute 在 phase<path> 事件时更新快照；日志使用容量受限的队列（如 EvictingQueue 或 LinkedList 上限 500 条）；快照在 complete<path> 后保留且 running=false。
- [x] P003: 后端：支持运行中安装的 SSE 重连 <!-- scope: src<path> -->
  - Acceptance: InstallTask 改为维护 List<SseEmitter>；install(key) 发现已有运行任务时，新建 SseEmitter 加入任务并立即发送当前快照的 info<path> 事件，然后返回新 emitter；重复请求不再抛 IllegalStateException。
- [x] P004: 后端：新增安装进度查询接口 <!-- scope: src<path> -->
  - Acceptance: 新增 GET <path> 200。
- [x] P005: 后端：扩展安装状态接口 <!-- scope: src<path> -->
  - Acceptance: GET <path> 返回 {installing, percentage, phase, status, message}，保留原有 installing 字段兼容旧前端。
- [x] P006: 前端：新增进度类型与 API 函数 <!-- scope: scheduled-task-ui<path> -->
  - Acceptance: 新增 InstallProgressSnapshot 接口与 getInstallProgress(key) 函数；getInstallStatus 返回类型更新为扩展字段；npm run type-check 通过。
- [x] P007: 前端：在全局 store 中管理安装进度 <!-- scope: scheduled-task-ui<path> -->
  - Acceptance: appStore 新增 installProgress: Record<string, InstallProgressSnapshot>、setInstallProgress(key, snapshot)、clearInstallProgress(key)；DashboardView 可通过 store 读取。
- [x] P008: 前端：移除安装弹窗并改为行内进度条 <!-- scope: scheduled-task-ui<path> -->
  - Acceptance: 删除 installDialogVisible<path> 及对应 el-dialog；在每个 dependencyStatusItem 行内，当 installingKey === dep.key 时渲染 el-progress。
- [x] P009: 前端：限制依赖检测面板高度并支持滚动 <!-- scope: scheduled-task-ui<path> -->
  - Acceptance: env-panel 添加 max-height: 360px 与 overflow-y: auto；移除或保留 VISIBLE_DEP_LIMIT，但不再依赖“还有 N 个未显示”来避免撑开页面；超过高度时出现滚动条。
- [x] P010: 前端：仅对当前安装项显示进度与日志入口 <!-- scope: scheduled-task-ui<path> -->
  - Acceptance: 进度条、阶段文本、“查看日志”按钮仅在 installingKey 匹配的依赖行显示；其他依赖行仅展示原状态与安装按钮；安装按钮在 isInstalling 时禁用。
- [x] P011: 前端：新增日志弹窗按钮 <!-- scope: scheduled-task-ui<path> -->
  - Acceptance: 新增 logDialogVisible、logDialogTitle、当前查看的 key；依赖行在 active 时显示“查看日志”按钮，点击打开 el-dialog 展示 installLogs（对应 key），弹窗内日志区域 max-height: 400px 并自动滚动到底部。
- [x] P012: 前端：实现刷新页面后的进度恢复 <!-- scope: scheduled-task-ui<path> -->
  - Acceptance: onMounted 在加载依赖列表后，对每个 dependencyItem 调用 getInstallProgress；若 snapshot.running=true，则恢复 installingKey、installProgress、installLogs 并立即重新建立 SSE 连接；后续 SSE 消息继续更新同一进度条。
- [x] P013: 集成：端到端流程验证 <!-- scope: 后端 + 前端 -->
  - Acceptance: 手动测试清单通过：点击“安装依赖”→页面行内出现进度条→只有当前项显示进度与日志按钮→刷新浏览器→进度与日志继续显示→点击“查看日志”弹出滚动日志窗口→安装完成后进度条消失、依赖状态刷新。
- [x] P014: 构建与类型检查 <!-- scope: 后端 + 前端 -->
  - Acceptance: mvn compile -q 成功；cd scheduled-task-ui && npm run type-check（或 npm run build）成功；无新增 TypeScript 类型错误。
- [x] P015: Final validation <!-- scope: validation -->
  - Acceptance: 1) 后端编译：mvn compile -q；2) 前端构建：cd scheduled-task-ui && npm run type-check && npm run build；3) 启动服务后，在 Dashboard 点击“安装依赖”，确认行内进度、单一条目显示、日志弹窗、刷新恢复四项需求均符合预期。
