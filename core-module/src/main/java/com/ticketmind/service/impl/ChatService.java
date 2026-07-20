package com.ticketmind.service.impl;

import com.ticketmind.agent.core.TicketAgent;
import com.ticketmind.agent.memory.SystemPromptMemoryManager;
import com.ticketmind.agent.workflow.TaskWorkflowEngine;
import com.ticketmind.common.BusinessException;
import com.ticketmind.common.ResultCode;
import com.ticketmind.context.UserContextHolder;
import com.ticketmind.model.dto.*;
import com.ticketmind.model.entity.ChatMessageRecord;
import com.ticketmind.model.entity.ChatMessageRole;
import com.ticketmind.model.entity.ChatSession;
import com.ticketmind.model.entity.UserAccount;
import com.ticketmind.repository.ChatMessageRecordRepository;
import com.ticketmind.repository.ChatSessionRepository;
import com.ticketmind.repository.UserAccountRepository;
import dev.langchain4j.service.TokenStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final long STREAM_TIMEOUT_MILLIS = 120_000L;

    private final TicketAgent ticketAgent;

    private final TaskWorkflowEngine taskWorkflowEngine;

    private final SystemPromptMemoryManager systemPromptMemoryManager;

    private final ChatSessionRepository chatSessionRepository;

    private final ChatMessageRecordRepository chatMessageRecordRepository;

    private final UserAccountRepository userAccountRepository;

    private final TransactionTemplate transactionTemplate;

    @Transactional
    public ChatResponse chat(Long sessionId, String message) {
        String prompt = requireMessage(message);
        ChatSession session = getOrCreateSession(sessionId, prompt);
        saveMessage(session, ChatMessageRole.USER, prompt);

        String memoryId = sessionMemoryId(session);
        String answer = taskWorkflowEngine.run(memoryId, prompt);
        systemPromptMemoryManager.markPromptInjected(memoryId);
        systemPromptMemoryManager.updateFromUserMessage(memoryId, prompt);
        saveMessage(session, ChatMessageRole.ASSISTANT, answer);
        touchSession(session);
        return new ChatResponse(session.getId(), answer);
    }

    public SseEmitter stream(Long sessionId, String message) {
        String prompt = requireMessage(message);
        ChatSession session = transactionTemplate.execute(status -> createSessionAndSaveUserMessage(sessionId, prompt));

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        StringBuilder answer = new StringBuilder();
        String memoryId = sessionMemoryId(Objects.requireNonNull(session));
        String systemPromptMemories = systemPromptMemoryManager.renderForSystemPrompt(memoryId);
        systemPromptMemoryManager.markPromptInjected(memoryId);
        systemPromptMemoryManager.updateFromUserMessage(memoryId, prompt);
        TokenStream tokenStream = ticketAgent.stream(memoryId, systemPromptMemories, prompt);

        tokenStream
                .onPartialResponse(token -> {
                    answer.append(token);
                    send(emitter, "token", token);
                })
                .onCompleteResponse(response -> {
                    transactionTemplate.executeWithoutResult(status -> saveAssistantReply(session.getId(), answer.toString()));
                    send(emitter, "done", new ChatResponse(session.getId(), answer.toString()));
                    emitter.complete();
                })
                .onError(emitter::completeWithError)
                .start();

        return emitter;
    }

    @Transactional(readOnly = true)
    public ChatHistoryResponse history(Long sessionId) {
        Long userId = requireUserId();
        ChatSession session = chatSessionRepository.findByIdAndUser_Id(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ResultCode.DATA_NOT_FOUND, "chat session not found"));
        List<ChatMessageHistoryItem> messages = chatMessageRecordRepository
                .findBySession_IdAndSession_User_IdOrderByCreatedAtAscIdAsc(session.getId(), userId)
                .stream()
                .map(message -> new ChatMessageHistoryItem(
                        message.getId(),
                        message.getRole(),
                        message.getContent(),
                        message.getCreatedAt()
                ))
                .toList();
        return new ChatHistoryResponse(session.getId(), messages);
    }

    @Transactional(readOnly = true)
    public ChatSessionListResponse sessions() {
        Long userId = requireUserId();
        List<ChatSessionListItem> sessions = chatSessionRepository.findByUser_IdOrderByUpdatedAtDescIdDesc(userId)
                .stream()
                .map(session -> new ChatSessionListItem(session.getId(), session.getTitle()))
                .toList();
        return new ChatSessionListResponse(sessions);
    }

    private ChatSession createSessionAndSaveUserMessage(Long sessionId, String prompt) {
        ChatSession session = getOrCreateSession(sessionId, prompt);
        saveMessage(session, ChatMessageRole.USER, prompt);
        return session;
    }

    private void saveAssistantReply(Long sessionId, String answer) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ResultCode.DATA_NOT_FOUND, "chat session not found"));
        saveMessage(session, ChatMessageRole.ASSISTANT, answer);
        touchSession(session);
    }

    private ChatSession getOrCreateSession(Long sessionId, String firstMessage) {
        Long userId = requireUserId();
        if (sessionId != null) {
            return chatSessionRepository.findByIdAndUser_Id(sessionId, userId)
                    .orElseThrow(() -> new BusinessException(ResultCode.DATA_NOT_FOUND, "chat session not found"));
        }

        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.DATA_NOT_FOUND, "user not found"));
        ChatSession session = new ChatSession();
        session.setUser(user);
        session.setTitle(buildTitle(firstMessage));
        return chatSessionRepository.save(session);
    }

    private void saveMessage(ChatSession session, ChatMessageRole role, String content) {
        ChatMessageRecord message = new ChatMessageRecord();
        message.setSession(session);
        message.setRole(role);
        message.setContent(content == null ? "" : content);
        chatMessageRecordRepository.save(message);
    }

    private void touchSession(ChatSession session) {
        session.setUpdatedAt(Instant.now());
        chatSessionRepository.save(session);
    }

    private String sessionMemoryId(ChatSession session) {
        return String.valueOf(session.getId());
    }

    private String requireMessage(String message) {
        if (!StringUtils.hasText(message)) {
            throw new BusinessException(ResultCode.MISSING_REQUIRED_PARAMETER, "message is required");
        }
        return message.strip();
    }

    private Long requireUserId() {
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.MISSING_TOKEN, "user context is required");
        }
        return userId;
    }

    private String buildTitle(String message) {
        String title = message.strip();
        if (title.length() <= 60) {
            return title;
        }
        return title.substring(0, 60);
    }

    private void send(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException exception) {
            throw new StreamSendException(exception);
        }
    }

    private static class StreamSendException extends RuntimeException {
        StreamSendException(Throwable cause) {
            super(cause);
        }
    }
}
