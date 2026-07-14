# MCP Server Module

`mcp-server` 是 TicketMind 的独立 MCP 服务模块，用于单独部署和扩展 MCP 协议相关能力。

## 当前职责

- 提供独立 Spring Boot 启动入口
- 承载 MCP 工具定义与对外暴露能力
- 与主业务应用解耦，便于单独部署、扩缩容和后续替换传输层

## 启动方式

```bash
mvn -pl mcp-server spring-boot:run
```

默认端口为 `18080`。

## 后续扩展建议

- 在该模块内补充正式的 MCP transport 配置，例如 `stdio` 或 `SSE`
- 将票务查询、订单查询等工具逐步迁入该模块
- 如果主应用需要消费 MCP 工具，可在 `ticket-mind-core` 增加 MCP client 配置
