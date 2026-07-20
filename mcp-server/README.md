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

默认端口为 `18080`，可通过 `MCP_SERVER_PORT` 覆盖，避免和主应用的 `SERVER_PORT` 混用。

## 发送通知邮件接口

新增 HTTP 接口：

```http
POST /internal/notifications/email
Content-Type: application/json
```

请求体示例：

```json
{
  "recipient": "user@example.com",
  "subject": "出票通知",
  "content": "您的订单已出票，请及时查看。"
}
```

同时也支持通过 MCP `tools/call` 调用 `send_notification_email` 工具。

邮件发送依赖以下环境变量：

```bash
TICKET_MIND_MAIL_HOST=smtp.example.com
TICKET_MIND_MAIL_PORT=587
TICKET_MIND_MAIL_USERNAME=your-username
TICKET_MIND_MAIL_PASSWORD=your-password
TICKET_MIND_MAIL_SMTP_AUTH=true
TICKET_MIND_MAIL_SMTP_STARTTLS_ENABLE=true
TICKET_MIND_NOTIFICATION_MAIL_FROM_ADDRESS=no-reply@example.com
```

## 后续扩展建议

- 在该模块内补充正式的 MCP transport 配置，例如 `stdio` 或 `SSE`
- 将票务查询、订单查询等工具逐步迁入该模块
- 如果主应用需要消费 MCP 工具，可在 `core-module` 增加 MCP client 配置
