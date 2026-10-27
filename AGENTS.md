# Agent 协作规范

MCP 工具服务 + 调度网关。技术栈：Java 21 / Spring Boot 3.5 / Spring AI MCP Server / MyBatis-Plus / MySQL 8.0 / Redis 7.2。

## 目录即架构

分包按「职责层」组织，目录结构直接反映依赖方向：

```text
mcport-server/src/main/java/com/mcport
├── api/           # MCP 端点与接入层
├── tool/          # 工具层（@McpTool）
├── governance/    # 治理层（filter / aspect / auth / ratelimit / audit / metrics / validator）
├── service/       # 业务逻辑
├── mapper/        # MyBatis-Plus Mapper
├── entity/        # 数据库实体
└── dto/           # 工具输入输出
```

依赖方向单向：`api / tool → governance → service → mapper`，反方向依赖不允许。细节见 [docs/编码约定.md](docs/编码约定.md)。

## git 相关

### 提交信息

commit message 格式：`<type>: <精炼描述>`，不堆砌内容。

type 取值：

| type | 含义 |
|------|------|
| `feat` | 新增功能 |
| `fix` | 修复 bug |
| `refactor` | 重构，不改变外部行为 |
| `perf` | 性能优化 |
| `docs` | 文档 |
| `test` | 测试 |
| `build` | 构建、依赖 |
| `chore` | 杂项，如配置、脚本 |

示例：

```text
feat: 实现 query_orders 工具
fix: 修复锁库存幂等流水缺失导致的重复扣减
build: 引入 spring-ai-starter-mcp-server-webmvc
chore: 初始化仓库与本地开发环境配置
```

### 提交粒度

- 每次提交控制在 50 行左右，便于 review；搭建骨架、文档、引入依赖等无法拆分的提交除外
- 一个提交只做一件事：功能、修复、重构不混在一起
- `target/`、`node_modules/`、日志、本地环境文件不进入 Git

### 分支

```text
main              # 主分支，保护分支
develop           # 开发分支
feature/xxx       # 功能分支，从 develop 切出
fix/xxx           # 修复分支
```

### 提交前检查

- 构建与测试通过（阶段 1 起为 `mvn test`）
- 未提交密钥：`.env`、`docker/.env`、`application-local.yml`

## 文档与配置

- 方案变更先改 `docs/`，再改代码
- 敏感信息只走环境变量，模板见 `.env.example` 与 `docker/.env.example`；新增变量必须同步模板
- 端口、库名、密码等本地差异通过 `.env` 覆盖，不硬编码
