# TicketMind

> 一个基于大语言模型的智能12306购票Agent，通过自然语言对话帮您完成从查票到购票的全流程。

---

## 项目简介

TicketMind 是一个智能购票助手，用户只需用日常语言告诉它出行需求（如"帮我买一张明天下午北京到上海的高铁票，二等座"），它就能自主完成查票、选方案、下单、候补、支付提醒等全流程操作。

核心价值在于 **"托管式购票体验"**——您提需求，Agent帮您执行，无需手动刷票和反复填表。

本项目基于多模块 Spring Boot 工程构建，包含主业务应用和独立的 MCP Server 部署模块。

---

## 功能模块

### 智能对话
理解用户的自然语言购票需求，支持多轮对话，信息不全时主动追问澄清。

### 票务查询
按出发地、目的地、日期、席别等条件查询余票、车次、票价和时刻信息。

### 换乘规划
无直达车次时，自动生成中转换乘方案，支持按总时长或换乘时间筛选。

### 购票下单
选定车次后自动提交订单，支持选择乘车人、座席偏好，并完成订单锁定。

### 订单管理
查看待支付、已支付、已取消等状态的订单，支持取消未支付订单。

### 候补购票
无票时自动提交候补订单，支持多车次、多日期组合，兑现成功后立即通知。

### 余票监控
持续监控目标车次的余票变化，一旦有票立即触发通知或自动下单。

### 任务托管
创建无人值守抢票任务，支持多策略、多优先级，任务状态随时可查可控。

### 行程管理
自动聚合即将出发的行程，出发前主动推送出行提醒。

### 退改签
支持在线退票和改签，改签目标无票时可自动监控等待。

### 账户与乘车人
管理 12306 账号登录态和常用乘车人信息，支持多账号切换。

### 智能通知
关键事件主动推送，包括有票提醒、候补成功、订单超时、出行提醒等。

---

## 技术特性

- 基于大语言模型（LLM）的意图理解与任务规划
- 遵循 MCP（模型上下文协议）标准化工具调用
- 支持任务异步托管与状态持久化
- 提供完整的对话记忆与上下文管理
- 模块化设计，工具可灵活扩展

---

## 快速开始

### 环境要求
- Java 17+
- Maven 3.6+
- OpenAI API Key（或兼容接口）
- RabbitMQ（仅在启用消息功能时需要）

### 模块说明

- `ticket-mind-core`：原有主业务应用，负责 Agent、业务逻辑、数据访问和 HTTP 接口。
- `mcp-server`：独立部署的 MCP 服务模块，专门承载对外暴露的工具服务。

### 运行

启动主应用：

```bash
export OPENAI_API_KEY=你的密钥
mvn -pl ticket-mind-core spring-boot:run
```

启用 RabbitMQ：

```bash
export RABBITMQ_ENABLED=true
export RABBITMQ_HOST=localhost
export RABBITMQ_PORT=5672
export RABBITMQ_USERNAME=guest
export RABBITMQ_PASSWORD=guest
mvn -pl ticket-mind-core spring-boot:run
```

启用后，`/knowledge/upload` 上传知识库文档成功会发送一条 `knowledge.uploaded` 消息到默认交换机 `ticket-mind.knowledge.exchange`，并由内置消费者接收。

启动 MCP Server：

```bash
mvn -pl mcp-server spring-boot:run
