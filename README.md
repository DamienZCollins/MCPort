# MCPort

生产级 MCP 工具服务 + 调度网关：用 Spring AI 实现 6 个真实业务的 MCP 工具，并以 AOP 切面统一提供鉴权、权限分级、限流、审计与越权防护。

> 当前进度：阶段 0（仓库与本地环境）。尚无可运行服务，下一步为第 1 周的项目骨架与 MCP Server 集成验证。

## 核心能力（规划）

1. **工具层**：6 个 MCP 工具，READ / WRITE 权限分离，真实读写 MySQL
   - 只读：`query_orders`、`check_inventory`、`get_order_detail`、`search_knowledge`
   - 写入：`update_order_status`、`lock_inventory`
2. **治理层**：API Key 鉴权（Redis 缓存 + MySQL 兜底）、权限校验、Redis 限流、异步审计、越权防护
3. **可观测性**：Micrometer 指标（调用量 / 延迟 / 拦截），Prometheus 端点暴露
4. **客户端兼容**：自研 Java Client + MCP Inspector 验证 Streamable HTTP 协议

详见 [总体方案](docs/总体方案.md) 与 [开发路径](docs/开发路径.md)。

## 技术栈

| 类别 | 选型 |
|------|------|
| 语言 | Java 21 |
| 后端 | Spring Boot 3.5.1 + Spring AI 1.1.4（MCP Server WebMVC） |
| 数据访问 | MyBatis-Plus 3.5.9 |
| 存储 | MySQL 8.0.40 + Redis 7.2 |
| 指标 | Micrometer + Prometheus |
| 本地环境 | Docker Compose |

不使用 Elasticsearch / Hutool / Sa-Token：`search_knowledge` 一期返回 Mock 数据，鉴权简化为 API Key。

## 目录结构

```text
MCPort/
├── mcport-server/          # 后端服务（第 1 周起）
│   └── src/main/java/com/mcport/
│       ├── api/            # MCP 端点与接入层
│       ├── tool/           # 工具层（@McpTool）
│       ├── governance/     # 治理层：aspect / auth / ratelimit / audit / validator
│       ├── service/        # 业务逻辑
│       ├── mapper/         # MyBatis-Plus Mapper
│       ├── entity/         # 数据库实体
│       └── dto/            # 工具输入输出
├── mcport-client-demo/     # 自研 Java Client（第 4 周）
├── docker/                 # 本地中间件：docker-compose.yml + 初始化脚本
│   └── init/               # 01-schema.sql 建表、02-data.sql 合成数据
├── docs/                   # 方案、开发路径、编码约定
├── .env.example            # 服务端环境变量模板
└── README.md
```

## 快速开始

### 1. 启动本地 MySQL 与 Redis

```bash
cp docker/.env.example docker/.env
```

编辑 `docker/.env` 设置 `MYSQL_ROOT_PASSWORD`，然后：

```bash
docker compose -f docker/docker-compose.yml --env-file docker/.env up -d
```

| 服务 | 宿主机端口 | 说明 |
|------|-----------|------|
| MySQL 8.0.40 | `3307`（默认） | 库 `mcport_db`；3306 常被占用，故改端口，可在 `docker/.env` 调整 |
| Redis 7.2 | `6379` | AOF 持久化 |

等待健康检查通过：

```bash
docker compose -f docker/docker-compose.yml --env-file docker/.env ps
```

验证：

```bash
docker exec mcport-mysql mysql -uroot -p -e "SELECT VERSION()"
docker exec mcport-redis redis-cli ping
```

### 2. 配置服务端环境变量

```bash
cp .env.example .env
```

编辑 `.env`，数据库密码与 `docker/.env` 保持一致。启动服务前导出：

```bash
set -a && source .env && set +a
```

### 停止 / 清理

```bash
docker compose -f docker/docker-compose.yml --env-file docker/.env down
```

数据卷 `mcport_mysql-data`、`mcport_redis-data` 保留；需彻底清空时追加 `-v`。

## 配置约定

- 连接信息一律走环境变量，代码与配置文件中不出现明文密码
- 新增环境变量必须同步更新 `.env.example` 与 `docker/.env.example`
- 本地覆盖用 `application-local.yml`（不提交）
- 表结构变更以 SQL 脚本管理，放在 `docker/init/`（或后续迁移目录），不手改

## 开发约定

见 [docs/编码约定.md](docs/编码约定.md)，提交信息格式见 [AGENTS.md](AGENTS.md)。
