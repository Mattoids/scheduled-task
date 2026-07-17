# OpenAPI 规范初始化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为当前 Spring Boot 后端生成并提交初始 OpenAPI 3.0 静态规范文件 `docs/openapi.yaml`，通过 springdoc-openapi 运行时导出并转换。

**Architecture:** 在项目中集成 `springdoc-openapi`，启动应用后从 `/v3/api-docs` 端点获取 JSON，使用 Ruby 内建 YAML 库转换为 YAML，保存到 `docs/openapi.yaml`，并更新 README 文档索引。

**Tech Stack:** Spring Boot 3.2.5, springdoc-openapi 2.5.0, Maven, Ruby (JSON/YAML 转换), MySQL (dev)

## Global Constraints

- Spring Boot 版本：`3.2.5`
- springdoc-openapi 版本：`2.5.0`
- 输出文件路径：`docs/openapi.yaml`
- 配置文件：`src/main/resources/application-dev.yml`
- 不修改现有 Controller、DTO、Entity 业务代码
- 保留 springdoc 依赖用于后续维护
- 本地 MySQL 需可访问，数据库 `scheduled_task` 可由 Flyway 自动初始化

## File Structure

- `pom.xml` — 新增 `springdoc-openapi-starter-webmvc-ui` 依赖
- `src/main/resources/application-dev.yml` — 新增 `springdoc` 配置（关闭 Swagger UI，保留 `/v3/api-docs`）
- `docs/openapi.yaml` — 生成的静态 OpenAPI 3.0 规范
- `README.md` — 在「文档」章节新增 OpenAPI 链接

---

### Task 1: 添加 springdoc-openapi 依赖

**Files:**
- Modify: `pom.xml`

**Interfaces:**
- Produces: Maven 项目新增 `springdoc-openapi-starter-webmvc-ui:2.5.0` 依赖

- [ ] **Step 1: 在 `pom.xml` dependencies 节末尾添加依赖**

```xml
        <!-- OpenAPI 文档生成 -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.5.0</version>
        </dependency>
```

具体位置：在 `spring-boot-starter-test` 依赖之前。

- [ ] **Step 2: 验证依赖可解析**

Run: `mvn dependency:resolve -q`
Expected: 命令成功退出，无 ERROR。

- [ ] **Step 3: 提交**

```bash
git add pom.xml
git commit -m "$(cat <<'EOF'
deps: 添加 springdoc-openapi 依赖用于生成 OpenAPI 规范

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: 配置 springdoc

**Files:**
- Modify: `src/main/resources/application-dev.yml`

**Interfaces:**
- Consumes: 已存在的 `application-dev.yml`
- Produces: `springdoc` 配置项，使 `/v3/api-docs` 可用且 Swagger UI 不暴露

- [ ] **Step 1: 在 `application-dev.yml` 末尾追加 springdoc 配置**

```yaml
springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    enabled: false
```

- [ ] **Step 2: 提交**

```bash
git add src/main/resources/application-dev.yml
git commit -m "$(cat <<'EOF'
config: 配置 springdoc-openapi 端点

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: 启动应用并导出 OpenAPI JSON

**Files:**
- 无文件修改，仅运行时操作

**Interfaces:**
- Consumes: Task 1 和 Task 2 的依赖与配置
- Produces: `/tmp/scheduled-task-openapi.json`（临时 OpenAPI JSON 文件）

- [ ] **Step 1: 确认 MySQL 可用**

Run: `mysql -h ${MYSQL_HOST:-localhost} -P ${MYSQL_PORT:-3306} -u ${MYSQL_USER:-root} -p${MYSQL_PASSWORD:-Yanping69!} -e "SELECT 1;" 2>/dev/null || echo "MySQL 连接失败，请检查本地数据库"`
Expected: 返回 `1` 或连接失败提示。若失败，需先启动本地 MySQL 并确保 `scheduled_task` 数据库可访问。

- [ ] **Step 2: 后台启动 Spring Boot 应用**

Run: `mvn spring-boot:run -Dspring-boot.run.profiles=dev > /tmp/scheduled-task-run.log 2>&1 &`
Expected: 命令立即返回，进程在后台运行。

- [ ] **Step 3: 等待应用启动完成**

Run: `for i in {1..60}; do if curl -s http://localhost:8080/v3/api-docs | head -c 10 | grep -q '{'; then echo "started"; break; fi; sleep 2; done`
Expected: 输出 `started`。若 120 秒内未启动，检查 `/tmp/scheduled-task-run.log`。

- [ ] **Step 4: 导出 OpenAPI JSON**

Run: `curl -s http://localhost:8080/v3/api-docs -o /tmp/scheduled-task-openapi.json`
Expected: 文件 `/tmp/scheduled-task-openapi.json` 存在且大小大于 0。

- [ ] **Step 5: 验证 JSON 格式**

Run: `jq empty /tmp/scheduled-task-openapi.json`
Expected: 命令成功退出，无输出。

---

### Task 4: 转换 JSON 为 YAML 并保存

**Files:**
- Create: `docs/openapi.yaml`

**Interfaces:**
- Consumes: `/tmp/scheduled-task-openapi.json`
- Produces: `docs/openapi.yaml`

- [ ] **Step 1: 确保 docs 目录存在**

Run: `mkdir -p docs`
Expected: 目录已存在或创建成功。

- [ ] **Step 2: 使用 Ruby 将 JSON 转换为 YAML**

Run: `ruby -ryaml -rjson -e 'puts YAML.dump(JSON.parse(File.read("/tmp/scheduled-task-openapi.json")))' > docs/openapi.yaml`
Expected: `docs/openapi.yaml` 存在且大小大于 0。

- [ ] **Step 3: 验证 YAML 语法**

Run: `ruby -ryaml -e 'YAML.load_file("docs/openapi.yaml"); puts "valid yaml"'`
Expected: 输出 `valid yaml`。

- [ ] **Step 4: 检查规范包含核心接口**

Run: `grep -E '"(/api/auth/login|/api/task|/api/public/tasks)' docs/openapi.yaml | head -5`
Expected: 输出包含 `/api/auth/login`、`/api/task` 或 `/api/public/tasks` 等路径。

- [ ] **Step 5: 提交**

```bash
git add docs/openapi.yaml
git commit -m "$(cat <<'EOF'
docs: 生成初始 OpenAPI 3.0 接口规范

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: 更新 README

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: 已存在的 README「文档」章节
- Produces: README 中新增 OpenAPI 规范链接

- [ ] **Step 1: 在 README「文档」章节增加链接**

找到以下行：
```markdown
- [使用手册](docs/USER_MANUAL.md) — 面向最终用户的图文操作指南，涵盖所有功能模块的使用说明和系统页面截图
```

在其后新增一行：
```markdown
- [OpenAPI 接口规范](docs/openapi.yaml) — 后端 REST API OpenAPI 3.0 规范
```

- [ ] **Step 2: 提交**

```bash
git add README.md
git commit -m "$(cat <<'EOF'
docs: README 增加 OpenAPI 接口规范链接

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: 清理与最终验证

**Files:**
- 无新增文件

**Interfaces:**
- Consumes: 后台 Spring Boot 进程

- [ ] **Step 1: 停止后台 Spring Boot 进程**

Run: `pkill -f "mvn spring-boot:run" || true`
Expected: 后台进程被终止（或原本已不存在）。

- [ ] **Step 2: 检查最终 git 状态**

Run: `git status --short`
Expected: 无未提交修改。

- [ ] **Step 3: 检查生成文件大小**

Run: `wc -l docs/openapi.yaml && ls -lh docs/openapi.yaml`
Expected: 文件行数大于 100，大小大于 10KB。

---

## Self-Review

**1. Spec coverage:**
- 添加 springdoc 依赖 ✅ Task 1
- 配置 springdoc 端点 ✅ Task 2
- 启动应用并导出规范 ✅ Task 3
- JSON 转 YAML 保存到 `docs/openapi.yaml` ✅ Task 4
- README 增加引用 ✅ Task 5
- 验证与清理 ✅ Task 6

**2. Placeholder scan:**
- 无 TBD/TODO
- 所有命令和代码完整
- 版本号明确

**3. Type consistency:**
- 文件路径一致：`docs/openapi.yaml`、`pom.xml`、`application-dev.yml`、`README.md`
- 版本号一致：`2.5.0`、`3.2.5`
