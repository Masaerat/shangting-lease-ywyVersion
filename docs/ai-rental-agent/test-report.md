# AI 租房闭环测试报告

更新时间：2026-08-05
分支：`agentRag`

## 已验证

| 范围 | 命令 | 结果 |
| --- | --- | --- |
| 后端单元回归 | `mvnw.cmd -pl web/web-app -Dtest=... test` | 31 tests passed，0 failures，0 errors |
| 后端打包 | `mvnw.cmd -pl web/web-app -am -DskipTests package` | 通过 |
| 本地数据库迁移 | `LocalLeaseMigrationIT`，显式启用本地迁移 | Flyway V4 通过，原业务数据计数保持 |
| H5 可复现安装 | `npm ci` | 通过 |
| H5 类型检查 | `npm run type-check` | 通过 |
| H5 单元测试 | `npm run test:unit` | 2 tests passed |
| H5 生产构建 | `npm run build` | 通过，531 modules transformed |
| H5 生产依赖审计 | `npm audit --omit=dev --audit-level=high` | 0 vulnerabilities |
| Compose 静态配置 | `docker compose config --services` | 9 个服务均被识别，包含 `rent-house-h5` |
| API 闭环 IT 编译 | `-Dtest=RentalAgentClosedLoopIT test` | 编译通过；Docker 不可用，1 test skipped |
| Playwright 场景发现 | `npx playwright test --list` | mobile/desktop 共 2 tests |

## 待 Docker 恢复后验证

- `AppointmentConfirmationServiceIT`：并发确认只生成一条预约和一条 Outbox。
- `AppointmentOutboxPublisherIT`：RabbitMQ 暂停后保持 `PENDING`，恢复后变为 `PUBLISHED`。
- `RentalAgentClosedLoopIT`：登录、fallback SSE、草稿、确认、幂等重放和预约列表完整 API 链路。
- `scripts/verify-compose.ps1` 和 `scripts/smoke-rental-agent.ps1`（旧的 `smoke-ai-agent.ps1` 仍保留兼容）。
- Playwright mobile `390x844` 与 desktop `1440x900` 的真实页面、截图和无重叠检查。
- 配置有效 GLM Key 后的 `mode=MODEL`、`RoomSearchTool` 与引用 smoke。

在上述项目实际运行通过前，不能将 Docker 端到端验收标记为完成。

## 验收标准映射

| # | 标准 | 当前证据 | 状态 |
| --- | --- | --- | --- |
| 1 | 全新 Compose 数据卷全部健康 | Compose 配置与 healthcheck 已静态解析 | 待运行 |
| 2 | 无 Key 完成完整业务闭环 | fallback/预约单测通过，API journey 已编译 | 待运行 |
| 3 | 有效 Key 使用 MODEL 与只读工具 | 保留既有模型引擎与 `RoomSearchTool` | 待可用 Key smoke |
| 4 | MySQL 房源与可定位引用 | fallback 测试验证结构化推荐与引用 | 部分通过 |
| 5 | 显式确认、幂等、越权保护 | 预约单测通过，并发容器 IT 已编译 | 部分通过 |
| 6 | RabbitMQ 故障恢复不丢事件 | Outbox 单测通过，恢复 IT 已编译 | 待运行 |
| 7 | 后端、H5、Playwright 全部通过 | 非 Docker 测试/构建通过，Playwright 已发现 | 部分通过 |
| 8 | README 命令可直接执行 | 命令、Compose、smoke 脚本已提供并完成语法检查 | 待运行 |
| 9 | 无密钥、真实数据、IDE 改动 | 敏感信息扫描无命中，`.idea/misc.xml` 未暂存 | 通过 |

## 已知项

- 开发工具链仍有 17 项 npm audit 告警，主要来自旧 Vite/Vitest/MockJS 链路；生产依赖为 0。剩余自动修复要求强制升级 Vite 8/Vitest 4 或依赖无修复版本，应作为独立框架升级处理。
- Vite 构建仍提示原模板的 `%VITE_API_TITLE%` 未配置和旧 `::v-deep` 语法，均不阻断产物生成。
