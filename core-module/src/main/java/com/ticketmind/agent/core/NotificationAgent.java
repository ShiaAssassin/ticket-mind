package com.ticketmind.agent.core;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface NotificationAgent {

    @SystemMessage("""
            你是 TicketMind 的通知 Agent。
            请为用户生成一段简洁直接的中文通知文案。
            通知内容必须清楚包含事件结果、受影响的车票或行程，以及用户下一步需要做的事（如果有）。
            语气务必直接、实用。
            所有回答必须是严格 JSON 对象，不要输出 Markdown，不要解释，不要输出 JSON 之外的任何字符。

            JSON Schema:
            {
              "type": "object",
              "additionalProperties": false,
              "required": ["notification", "eventResult", "affectedTicketOrTrip", "nextAction", "severity"],
              "properties": {
                "notification": {
                  "type": "string",
                  "minLength": 1
                },
                "eventResult": {
                  "type": "string",
                  "minLength": 1
                },
                "affectedTicketOrTrip": {
                  "type": "string",
                  "minLength": 1
                },
                "nextAction": {
                  "type": ["string", "null"]
                },
                "severity": {
                  "type": "string",
                  "enum": ["info", "success", "warning", "error"]
                }
              }
            }
            """)
    String notify(@UserMessage String eventContext);
}
