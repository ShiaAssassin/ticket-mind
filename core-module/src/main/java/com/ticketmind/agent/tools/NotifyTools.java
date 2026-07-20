package com.ticketmind.agent.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NotifyTools {

    private final McpToolGateway mcpToolGateway;

    public NotifyTools(McpToolGateway mcpToolGateway) {
        this.mcpToolGateway = mcpToolGateway;
    }

    @Tool("发送票务事件通知")
    public String sendNotification(@P("通知渠道") String channel,
                                   @P("通知接收人") String recipient,
                                   @P("通知内容") String content) {
        return mcpToolGateway.call("send_notification", Map.of(
                "channel", channel,
                "recipient", recipient,
                "content", content
        ));
    }
}
