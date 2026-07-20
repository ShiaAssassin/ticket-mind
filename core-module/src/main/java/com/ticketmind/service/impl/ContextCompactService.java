package com.ticketmind.service.impl;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.ticketmind.agent.core.SummaryAgent;
import com.ticketmind.config.AgentProperties;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Four-layer LangChain4j chat context compaction pipeline.
 *
 * <p>Execution order: large tool result spillover, message-count trimming,
 * earlier tool result placeholder compaction, summary, emergency truncation.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextCompactService {

    private static final Encoding DEFAULT_ENCODING =
            Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile(
            "(?is)(tool[_ -]?call|function[_ -]?call|调用工具|工具调用|工具请求|\"tool_calls\"|\"toolCalls\"|\"function_call\")");

    private static final Pattern TOOL_RESULT_PATTERN = Pattern.compile(
            "(?is)(tool[_ -]?result|function[_ -]?result|observation|工具返回|工具结果|调用结果|执行结果)");

    private final SummaryAgent summaryAgent;

    private final AgentProperties properties;

    public List<ChatMessage> compact(Object memoryId, List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        List<ChatMessage> compacted = new ArrayList<>(messages);
        String sessionId = memoryId == null ? null : memoryId.toString();
        compacted = toolResultBudget(sessionId, compacted);
        compacted = snipCompact(compacted);
        compacted = microCompact(compacted);
        compacted = summarize(compacted);
        compacted = reactiveCompact(compacted);
        return compacted;
    }

    public int countTokens(List<ChatMessage> messages) {
        return DEFAULT_ENCODING.countTokens(formatConversation(messages));
    }

    private List<ChatMessage> toolResultBudget(String sessionId, List<ChatMessage> messages) {
        AgentProperties.ContextCompact config = config();
        int maxChars = Math.max(1, config.getToolResultMaxChars());
        Set<Integer> toolResultIndexes = toolResultIndexes(messages);
        List<ChatMessage> result = new ArrayList<>(messages.size());

        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            String content = content(message);
            if (!toolResultIndexes.contains(i) || content.length() <= maxChars) {
                result.add(message);
                continue;
            }

            String storedPath = storeToolResult(sessionId, i, content);
            if (storedPath == null) {
                result.add(message);
                continue;
            }
            result.add(rewrite(message, String.format(config.getStoredToolResultPlaceholder(), storedPath)));
        }
        return result;
    }

    private List<ChatMessage> snipCompact(List<ChatMessage> messages) {
        AgentProperties.ContextCompact config = config();
        List<ChatMessage> cleaned = deleteGarbageRounds(messages);
        int threshold = Math.max(2, config.getMessageThreshold());
        if (cleaned.size() <= threshold) {
            return cleaned;
        }

        int headCount = Math.max(0, Math.min(config.getHeadMessageCount(), cleaned.size()));
        int tailCount = Math.max(0, threshold - headCount);
        int tailStart = Math.max(headCount, cleaned.size() - tailCount);
        boolean[] keep = new boolean[cleaned.size()];

        for (int i = 0; i < headCount; i++) {
            keep[i] = true;
        }
        for (int i = tailStart; i < cleaned.size(); i++) {
            keep[i] = true;
        }
        protectToolPairs(cleaned, keep);

        List<ChatMessage> result = new ArrayList<>();
        for (int i = 0; i < cleaned.size(); i++) {
            if (keep[i]) {
                result.add(cleaned.get(i));
            }
        }
        return result;
    }

    private List<ChatMessage> microCompact(List<ChatMessage> messages) {
        AgentProperties.ContextCompact config = config();
        int keepRecent = Math.max(0, config.getRecentToolResultCount());
        Set<Integer> toolResultIndexes = toolResultIndexes(messages);
        Set<Integer> recentToolResultIndexes = new HashSet<>();
        int seen = 0;

        for (int i = messages.size() - 1; i >= 0; i--) {
            if (!toolResultIndexes.contains(i)) {
                continue;
            }
            if (seen < keepRecent) {
                recentToolResultIndexes.add(i);
            }
            seen++;
        }

        List<ChatMessage> result = new ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (toolResultIndexes.contains(i) && !recentToolResultIndexes.contains(i)) {
                result.add(rewrite(message, config.getEarlierToolResultPlaceholder()));
            } else {
                result.add(message);
            }
        }
        return result;
    }

    private List<ChatMessage> summarize(List<ChatMessage> messages) {
        int threshold = tokenThreshold();
        if (countTokens(messages) <= threshold) {
            return messages;
        }

        int tailStart = summarizeTailStart(messages);
        List<ChatMessage> historyToSummarize = messages.subList(0, tailStart);
        List<ChatMessage> recentTail = messages.subList(tailStart, messages.size());
        if (historyToSummarize.isEmpty()) {
            return messages;
        }

        try {
            String summary = summaryAgent.summarize(formatConversation(historyToSummarize));
            if (!StringUtils.hasText(summary)) {
                return messages;
            }
            List<ChatMessage> result = new ArrayList<>(recentTail.size() + 1);
            result.add(new SystemMessage("上下文摘要:\n" + summary.strip()));
            result.addAll(recentTail);
            return result;
        } catch (Exception exception) {
            log.warn("Context summary failed, emergency truncation will be used if still over threshold", exception);
            return messages;
        }
    }

    private List<ChatMessage> reactiveCompact(List<ChatMessage> messages) {
        int threshold = tokenThreshold();
        if (countTokens(messages) <= threshold) {
            return messages;
        }

        int targetTokens = Math.max(1, (int) Math.floor(threshold * emergencyTargetRatio()));
        List<MessageBlock> blocks = messageBlocks(messages);
        List<ChatMessage> selected = new ArrayList<>();
        int selectedTokens = 0;

        for (int i = blocks.size() - 1; i >= 0; i--) {
            MessageBlock block = blocks.get(i);
            List<ChatMessage> blockMessages = messages.subList(block.start(), block.end());
            int blockTokens = countTokens(blockMessages);
            if (selectedTokens + blockTokens <= targetTokens) {
                selected.addAll(0, blockMessages);
                selectedTokens += blockTokens;
                continue;
            }
            if (selected.isEmpty() && block.size() == 1 && !isToolCall(messages.get(block.start()))) {
                ChatMessage truncated = truncateSingleMessage(messages.get(block.start()), targetTokens);
                if (countTokens(List.of(truncated)) <= targetTokens) {
                    selected.add(truncated);
                }
                break;
            }
        }

        return selected;
    }

    private ChatMessage truncateSingleMessage(ChatMessage message, int targetTokens) {
        String content = content(message);
        String suffix = "\n[Context hard-truncated.]";
        int low = 0;
        int high = content.length();
        int best = 0;

        while (low <= high) {
            int mid = low + (high - low) / 2;
            ChatMessage candidate = rewrite(message, content.substring(content.length() - mid) + suffix);
            if (countTokens(List.of(candidate)) <= targetTokens) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return rewrite(message, content.substring(content.length() - best) + suffix);
    }

    private List<ChatMessage> deleteGarbageRounds(List<ChatMessage> messages) {
        return messages.stream()
                .filter(message -> !isGarbage(message))
                .toList();
    }

    private boolean isGarbage(ChatMessage message) {
        if (message == null || !StringUtils.hasText(content(message))) {
            return true;
        }
        String normalized = content(message).strip().toLowerCase(Locale.ROOT);
        return normalized.equals("null")
                || normalized.equals("undefined")
                || normalized.equals("{}")
                || normalized.equals("[]");
    }

    private void protectToolPairs(List<ChatMessage> messages, boolean[] keep) {
        for (int i = 0; i < messages.size() - 1; i++) {
            if (!isToolCall(messages.get(i)) || !isToolResult(messages, i + 1)) {
                continue;
            }
            if (keep[i] || keep[i + 1]) {
                keep[i] = true;
                keep[i + 1] = true;
            }
        }
    }

    private Set<Integer> toolResultIndexes(List<ChatMessage> messages) {
        Set<Integer> indexes = new HashSet<>();
        for (int i = 0; i < messages.size(); i++) {
            if (isToolResult(messages, i)) {
                indexes.add(i);
            }
        }
        return indexes;
    }

    private List<MessageBlock> messageBlocks(List<ChatMessage> messages) {
        List<MessageBlock> blocks = new ArrayList<>();
        int index = 0;
        while (index < messages.size()) {
            if (index < messages.size() - 1
                    && isToolCall(messages.get(index))
                    && isToolResult(messages, index + 1)) {
                blocks.add(new MessageBlock(index, index + 2));
                index += 2;
            } else {
                blocks.add(new MessageBlock(index, index + 1));
                index++;
            }
        }
        return blocks;
    }

    private int summarizeTailStart(List<ChatMessage> messages) {
        int tailSize = Math.min(messages.size(), Math.max(4, config().getRecentToolResultCount() * 2 + 2));
        int tailStart = messages.size() - tailSize;
        while (tailStart > 0 && isToolResult(messages, tailStart) && isToolCall(messages.get(tailStart - 1))) {
            tailStart--;
        }
        return tailStart;
    }

    private boolean isToolCall(ChatMessage message) {
        if (message instanceof AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
            return true;
        }
        return message != null && TOOL_CALL_PATTERN.matcher(content(message)).find();
    }

    private boolean isToolResult(List<ChatMessage> messages, int index) {
        ChatMessage message = messages.get(index);
        if (message == null) {
            return false;
        }
        if (message.type() == ChatMessageType.TOOL_EXECUTION_RESULT) {
            return true;
        }
        if (TOOL_RESULT_PATTERN.matcher(content(message)).find()) {
            return true;
        }
        return index > 0
                && isToolCall(messages.get(index - 1))
                && message.type() != ChatMessageType.USER
                && StringUtils.hasText(content(message));
    }

    private String storeToolResult(String sessionId, int index, String content) {
        try {
            Path directory = localStoreDirectory();
            Files.createDirectories(directory);
            String fileName = safeFilePart(sessionId) + "-" + Instant.now().toEpochMilli() + "-" + index + ".txt";
            Path target = directory.resolve(fileName).normalize();
            Files.writeString(target, content, StandardCharsets.UTF_8);
            return target.toAbsolutePath().toString();
        } catch (IOException exception) {
            log.warn("Failed to store large tool result for sessionId={}", sessionId, exception);
            return null;
        }
    }

    private String formatConversation(List<ChatMessage> messages) {
        StringBuilder builder = new StringBuilder();
        for (ChatMessage message : messages) {
            builder.append(roleName(message))
                    .append(": ")
                    .append(content(message))
                    .append('\n');
        }
        return builder.toString();
    }

    private ChatMessage rewrite(ChatMessage message, String content) {
        if (message instanceof ToolExecutionResultMessage toolResult) {
            return ToolExecutionResultMessage.from(toolResult.id(), toolResult.toolName(), content);
        }
        if (message instanceof SystemMessage) {
            return new SystemMessage(content);
        }
        if (message instanceof UserMessage userMessage) {
            return StringUtils.hasText(userMessage.name())
                    ? new UserMessage(userMessage.name(), content)
                    : new UserMessage(content);
        }
        if (message instanceof AiMessage aiMessage && !aiMessage.hasToolExecutionRequests()) {
            return new AiMessage(content);
        }
        return new SystemMessage(content);
    }

    private String roleName(ChatMessage message) {
        if (message == null || message.type() == null) {
            return "UNKNOWN";
        }
        return message.type().name();
    }

    private String content(ChatMessage message) {
        if (message == null) {
            return "";
        }
        if (message instanceof SystemMessage systemMessage) {
            return systemMessage.text();
        }
        if (message instanceof UserMessage userMessage) {
            return userMessage.hasSingleText() ? userMessage.singleText() : userMessage.contents().toString();
        }
        if (message instanceof AiMessage aiMessage) {
            String text = aiMessage.text() == null ? "" : aiMessage.text();
            if (!aiMessage.hasToolExecutionRequests()) {
                return text;
            }
            return text + "\nTOOL_CALLS: " + aiMessage.toolExecutionRequests();
        }
        if (message instanceof ToolExecutionResultMessage toolResult) {
            return toolResult.text();
        }
        return message.toString();
    }

    private Path localStoreDirectory() {
        String directory = config().getLocalStoreDirectory();
        if (!StringUtils.hasText(directory)) {
            directory = "context-compact/tool-results";
        }
        return Paths.get(directory).toAbsolutePath().normalize();
    }

    private String safeFilePart(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown-session";
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private int tokenThreshold() {
        return Math.max(1, config().getTokenThreshold());
    }

    private double emergencyTargetRatio() {
        double ratio = config().getEmergencyTargetRatio();
        if (ratio <= 0 || ratio > 1) {
            return 0.8;
        }
        return ratio;
    }

    private AgentProperties.ContextCompact config() {
        return properties.getContextCompact();
    }

    private record MessageBlock(int start, int end) {
        int size() {
            return end - start;
        }
    }
}
