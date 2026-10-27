# Trading Platform

这是一个模拟交易与机器学习研究平台。阶段一的撮合、风控和结算、阶段二的实时行情与回测保持不变；阶段三新增 Python 特征工程、数据集版本、模型训练、Walk-forward 验证和结果回写。代码保留了中文注释，便于逐层阅读。

## 模块与依赖方向

```text
matching-api ───────────────┐
                            ▼
matching-infrastructure → matching-application → matching-engine → matching-domain
                                      └──────────────────────────→ matching-domain

matching-benchmark ────────────────────────────→ matching-engine
matching-integration-tests ────────────────────→ matching-api
trading-web ──HTTP / WebSocket────────────────→ matching-api
trading-analytics ─────────────────────────────→ matching-domain
ml-research ──读取行情 / 回写实验──────────────→ matching-api
```

- `matching-domain`：ID、枚举、价格/数量/金额、订单、账户、持仓和成交。
- `matching-engine`：订单簿、纯撮合引擎、不可变撮合输出。
- `matching-application`：下单/撤单编排、风控、结算、幂等、事务端口、恢复和并发命令处理。
- `trading-analytics`：CSV 行情、回放、SMA/突破/定投策略、回测引擎和异步任务。
- `matching-infrastructure`：PostgreSQL、Redis 与 Spring 事务适配器。
- `matching-api`：Spring Boot 启动入口、REST DTO、Controller 与异常映射。
- `matching-benchmark`：JMH 基准入口。
- `matching-integration-tests`：阶段验收所需的端到端场景。
- `trading-web`：Next.js + TypeScript 交易工作台，包含盘口、下单、成交和回测面板。
- `ml-research`：FastAPI + Pandas + scikit-learn 研究服务，包含数据清洗、版本化特征集、Ridge 模型、扩展窗口验证和实验产物。
- `monitoring`：Prometheus 抓取配置与自动配置的 Grafana 仪表板。

`matching-domain` 和 `matching-engine` 没有 Spring、数据库或 Redis 依赖。

## 建议阅读顺序

建议不要从 Controller 开始，按下面顺序理解实现，并用对应测试验证规则：

1. `Price`、`Quantity`、`Money` 与各类 ID；
2. `Order` 的构造约束与状态转换；
3. `OrderBook` 的双向价格排序、同价 FIFO 和撤单索引；
4. `MatchingEngine` 的限价/市价、部分成交和跨档成交；
5. `Account`、`Position`、风控、冻结、结算与资产守恒；
6. 内存幂等存储和 `SymbolCommandProcessor`；
7. 应用服务的事务编排；
8. PostgreSQL/Redis 适配器和订单簿恢复；
9. REST 映射与统一错误响应；
10. Python 数据清洗、特征生成和 Walk-forward 验证；
11. ML 结果回写与 Web 研究面板；
12. 集成验收与 JMH 三类负载。

## 构建与运行

要求 Java 21+、Maven 3.9+；单独开发前端时还需要 Node.js 22+；单独开发研究服务建议 Python 3.12。

```bash
mvn verify
docker compose up -d
mvn -pl matching-api -am package
java -jar matching-api/target/matching-api-0.1.0-SNAPSHOT.jar
```

直接执行 `docker compose up --build` 可启动完整环境：交易终端 `http://localhost:3000`、API `http://localhost:8080`、ML 服务 `http://localhost:8090`、Prometheus `http://localhost:9090`、Grafana `http://localhost:3001`。API 健康检查为 `/actuator/health/readiness`，ML 健康检查为 `/health`。

前端单独运行：

```bash
cd trading-web
npm install
npm run dev
```

研究服务测试与运行：

```bash
docker build --target test -t trading-ml-research-test ./ml-research
docker compose up -d --build ml-research
```

## 单元测试

项目现已提供三个阶段的测试。运行顺序、固定业务规则和失败诊断见 [TESTING.md](TESTING.md)，机器学习任务定义与实验边界见 [阶段三总结.md](阶段三总结.md)。

测试已按模块放在对应的 `src/test/java` 中，主要测试类包括：

- `PriceTest`：`1.0` 与 `1.00` 业务相等、非法精度和非正价格；
- `OrderTest`：部分成交、完全成交、终态不可再成交、市场单不能带限价；
- `OrderBookTest`：买高优先、卖低优先、同价 FIFO、撤单后清空空档；
- `MatchingEngineTest`：文档列出的 10 个必测撮合场景；
- `DefaultSettlementServiceTest`：限价改善退款、部分成交冻结、撤单解冻、资产守恒；
- `InMemoryIdempotencyStoreTest`：串行/并发重复、相同键不同请求冲突；

此外，`TradingFlowIT` 提供 10 条不依赖外部服务的交易流验收；PostgreSQL 和 HTTP 的 Testcontainers 场景可作为进阶练习继续补充。

## 关键设计约束

- 撮合成交价始终取 maker 价格。
- 市价单剩余数量取消，不进入订单簿。
- 时间与成交 ID 从外部注入，测试不能依赖真实时钟或随机顺序。
- 下单冻结与订单保存必须处于清晰的事务边界。
- 数据库是权威状态；Redis 只做缓存、协调或幂等加速。
- 恢复时按稳定序列重建挂单，不能重新撮合。
- 任何时刻可用/冻结资产均不得为负，系统总现金和总持仓必须守恒。

## 阶段完成定义

- 至少 30 个领域与撮合单元测试；
- 至少 10 个交易集成测试；
- FIFO、资产守恒、幂等和恢复均有自动化证据；
- JMH 报告包含硬件、JDK、堆参数、负载分布、吞吐量和延迟分位数；
- `matching-engine` 始终保持纯 Java。
