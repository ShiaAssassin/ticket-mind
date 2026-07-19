# AGENT.md

## 1. 项目概述

TicketMind 是一个基于大语言模型的 12306 智能购票 Agent，用自然语言对话完成查票、购票及相关票务流程。

## 2. 技术栈

- 语言：Java 17
- 框架：Spring Boot 3.3.5、LangChain4j 1.0.0-beta4
- 数据库：JPA、Redis

## 2.1 模块结构

- `core-module`：核心业务模块，承载原有 Agent、业务逻辑和接口。
- `mcp-server`：独立 MCP 服务模块，专门用于部署 MCP Server 能力。
- `common`: 公共模块，包含全局的异常处理，和响应体、业务码的定义。

## 3. 代码风格

- 优先使用最小实现，能用简单代码解决的问题，不引入额外抽象。
- 不要引入冗余依赖；新增依赖前必须确认现有能力无法覆盖。
- 不要加入复杂的兜底机制；只处理明确存在的业务场景和已知异常。
- 业务逻辑保持直观，避免过度封装、过度设计和提前扩展。
- **不用编译，不用写测试代码。**

## 4. 目录职责

- `core-module/src/main/java/com/ticketmind`：主业务应用主包，放 Spring Boot 启动类和项目核心代码。
- `core-module/src/main/resources`：放主业务应用配置文件，例如 `application.yml`。
- `core-module/src/main/java/com/ticketmind/agent`：放 Agent 定义、对话入口、系统提示词和模型服务接口。
- `core-module/src/main/java/com/ticketmind/agent/core`：放 Agent 核心编排代码，例如意图理解、任务规划、工具调用流程控制等。
- `core-module/src/main/java/com/ticketmind/agent/memory`：放 Agent 记忆相关代码，例如会话上下文、用户偏好、历史对话摘要等。
- `core-module/src/main/java/com/ticketmind/agent/prompt`：放提示词模板、系统提示词构建和提示词参数组装代码。
- `core-module/src/main/java/com/ticketmind/tools`：放 LangChain4j 工具方法，工具应保持小而明确，只暴露 Agent 需要调用的能力。
- `core-module/src/main/java/com/ticketmind/tools/impl`：放工具能力的具体实现，负责对接内部服务或外部依赖，不直接承载 Agent 对话逻辑。
- `core-module/src/main/java/com/ticketmind/tools/mcp`：放 MCP 工具适配代码，例如 MCP Server/Client 配置、工具声明、协议参数转换等。
- `core-module/src/main/java/com/ticketmind/tools/registry`：放工具注册、工具元数据维护和工具发现相关代码。
- `core-module/src/main/java/com/ticketmind/config`：放 Spring、LangChain4j、MCP、线程池、序列化等项目级配置类。
- `core-module/src/main/java/com/ticketmind/controller`：放 HTTP 接口层，只做参数接收、简单校验和响应包装，不写复杂业务逻辑。
- `core-module/src/main/java/com/ticketmind/service`：放业务编排和核心业务逻辑，例如查票、购票流程、订单状态处理等。
- `core-module/src/main/java/com/ticketmind/repository`：放数据访问代码，只负责持久化读写和查询封装。
- `core-module/src/test/java/com/ticketmind`：放核心业务测试代码，测试包结构应尽量和主代码包结构一致。

- `mcp-server/src/main/java/com/ticketmind/mcpserver`：放 MCP Server 启动类、配置和工具暴露代码。
- `mcp-server/src/main/resources`：放 MCP Server 独立配置。
- `mcp-server/src/test/java/com/ticketmind/mcpserver`：放 MCP Server 测试代码。

- `common/src/main/java/com/ticketmind/common`：放通用返回体、业务码、业务异常、全局异常处理等跨模块基础代码。

新增目录时遵循以下约定：

- `controller`：放 HTTP 接口层，只做参数接收、简单校验和响应包装，不写复杂业务逻辑。
- `service`：放业务编排和核心业务逻辑。
- `repository` 或 `mapper`：放数据访问代码。
- `model`：放领域模型、实体和值对象。
- `dto`：放接口入参、出参对象。
- `config`：放 Spring 配置类。

## 5. 业务码规则

- 统一使用 `common/ResultCode` 定义业务码，不在业务代码中硬编码 magic number。
- `0` 表示成功，只能用于成功响应。
- 失败响应必须使用非 `0` 业务码，并通过 `Result.fail(code, message)` 或 `Result.fail(ResultCode)` 返回。
- 业务码按 HTTP 语义分段：`400xx` 表示请求参数问题，`401xx` 表示认证问题，`403xx` 表示权限问题，`404xx` 表示资源不存在，`408xx` 表示超时，`500xx` 表示服务端或依赖异常。
- 新增业务码必须补充清晰的默认 `message` 和对应的 `HttpStatus`。
- 业务异常使用 `BusinessException` 抛出，由 `GlobalExceptionHandler` 统一转换为响应体。
- 不要为了临时文案新增业务码；只有可复用、可稳定识别的错误类型才进入 `ResultCode`。

### 业务码速查表

|  错误码  | HTTP状态 | 说明             |
|:-----:|:------:|:---------------|
|   0   |  200   | 操作成功           |
| 40001 |  400   | 缺少必填参数         |
| 40002 |  400   | 参数格式错误         |
| 40003 |  400   | 参数类型不匹配        |
| 40004 |  400   | 参数值长度超出限制      |
| 40005 |  400   | 请求体JSON格式错误    |
| 40006 |  400   | 请求头缺少必要字段      |
| 40021 |  400   | 请求体大小超出限制      |
| 40101 |  401   | 缺少token        |
| 40103 |  401   | Token无效        |
| 40104 |  401   | Accesstoken过期  |
| 40105 |  401   | RefreshToken过期 |
| 40106 |  401   | 用户名/密码错误       |
| 40301 |  403   | 没有接口访问权限       |
| 40302 |  403   | 没有数据访问权限       |
| 40303 |  403   | 没有数据操作权限       |
| 40401 |  404   | 接口不存在          |
| 40402 |  404   | 数据不存在          |
| 40403 |  404   | OSS文件不存在       |
| 40801 |  408   | 数据库查询超时        |
| 50001 |  500   | 服务器内部出现未知错误    |
| 50002 |  500   | OSS服务异常        |
| 50003 |  500   | 数据库异常          |
| 50004 |  500   | 缓存异常           |
| 50005 |  500   | 消息队列异常         |

## 6. 响应体规则

- HTTP 接口统一返回 `Result<T>`，响应字段保持为 `code`、`message`、`data`。
- 成功响应使用 `Result.success()`、`Result.success(data)` 或 `Result.success(message, data)`。
- 失败响应使用 `Result.fail(...)`，不要手动拼接 Map 或自定义响应结构。
- `code` 表示业务码，不直接等同于 HTTP 状态码。
- `message` 面向调用方，保持简短、明确、可读。
- `data` 只放业务数据；无数据时保持为空，由 `JsonInclude.NON_NULL` 省略。
- 参数错误、业务异常和未预期异常由 `GlobalExceptionHandler` 统一处理，Controller 不重复捕获同类异常。
- HTTP 状态码由 `ResultCode` 中的 `HttpStatus` 决定，响应体中的 `code` 用于前端或调用方判断具体业务结果。
