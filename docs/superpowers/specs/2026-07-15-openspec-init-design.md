# OpenAPI 规范初始化设计

## 背景

当前 `java-scheduled-task` 项目是一个基于 Spring Boot 3 的定时任务报表系统，REST 接口已覆盖任务管理、SQL 配置、爬取配置、通知、存储、RBAC、审计等多个模块，但项目尚未维护任何 OpenAPI/Swagger 接口规范文档。为了便于前后端协作、外部集成以及接口文档化，需要生成一份初始的 OpenAPI 3.0 静态规范文件。

## 目标

为 Java 后端生成一份 **OpenAPI 3.0 静态 YAML 规范文件**，覆盖所有 Controller 暴露的 REST 接口，作为项目接口文档的基线。

## 方案选择

在三种可行方案中，选择 **方案 A：springdoc-openapi 运行时生成后导出**。

- **方案 A（推荐）**：临时/长期集成 `springdoc-openapi`，启动应用后通过 `/v3/api-docs` 端点导出规范。准确性最高，能自动识别 Spring 注解、泛型返回值、`@Valid` 校验等。
- **方案 B（源码静态扫描）**：使用 JavaParser 解析 Controller 源码。不依赖运行时，但对泛型、`Result<T>` 包装、Spring 类型转换处理复杂，准确性低。
- **方案 C（字节码反射扫描）**：编译后用 Reflections 扫描 Controller 字节码。无需完整启动应用，但仍需编译成功，且复杂类型解析仍需额外处理。

选择方案 A 的原因是：当前代码没有任何 OpenAPI 注解，运行时反射能最完整、最准确地生成可用规范，且 springdoc 是 Spring Boot 3 生态中的标准做法。

## 实现流程

1. 在 `pom.xml` 添加依赖 `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0`（兼容 Spring Boot 3.2.5）。
2. 在 `application-dev.yml` 中可选配置：
   - `springdoc.api-docs.path=/v3/api-docs`
   - `springdoc.swagger-ui.enabled=false`（仅保留规范端点，不启用 UI）
3. 使用 `mvn spring-boot:run -Dspring-boot.run.profiles=dev` 启动应用。
4. 应用启动后，请求 `GET http://localhost:8080/v3/api-docs` 获取 OpenAPI JSON。
5. 将 JSON 转换为 YAML，保存到 `docs/openapi.yaml`。
6. 验证文件格式有效性，并在 README 文档章节增加引用链接。

## 运行环境要求

- 本地 MySQL 需存在 `scheduled_task` 数据库（首次启动时 Flyway 会自动执行迁移脚本建表）。
- 不需要企业微信、OSS、SMTP、Slack 等外部服务可达。
- 需要 `jq` 或 Python 用于 JSON 到 YAML 的转换。

## 输出位置

- 静态规范文件：`docs/openapi.yaml`
- README 引用：在「文档」章节增加 `- [OpenAPI 接口规范](docs/openapi.yaml)`

## 依赖保留策略

推荐 **保留 springdoc 依赖**，原因如下：

- 后续接口变更后可直接重新生成规范，无需重复配置。
- 团队可直接通过 `/v3/api-docs` 查看最新规范，也可对接 Swagger UI。
- 依赖体积小，对生产环境无负面影响（不启用 UI 时无额外端点暴露风险）。

如只需一次性生成，可在生成后从 `pom.xml` 移除依赖。

## 验证方式

- 使用 Swagger Editor 打开 `docs/openapi.yaml`，检查无语法错误且接口列表完整。
- 确认规范中包含所有 Controller 的基础路径、HTTP 方法、路径参数、查询参数和请求体结构。

## 范围说明

- 本次仅生成初始规范文件，不强制要求为所有字段补充 `description`、`example` 等 OpenAPI 注解。
- 不修改现有 Controller、DTO、Entity 的业务代码，仅添加 springdoc 依赖和配置。
- 生成的规范可能因缺少注解而字段描述较简略，后续可逐步补充 `@Schema` 注解完善。
