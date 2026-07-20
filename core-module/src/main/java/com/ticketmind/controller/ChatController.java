package com.ticketmind.controller;

import com.ticketmind.common.Result;
import com.ticketmind.model.dto.ChatHistoryResponse;
import com.ticketmind.model.dto.ChatRequest;
import com.ticketmind.model.dto.ChatResponse;
import com.ticketmind.model.dto.ChatSessionListResponse;
import com.ticketmind.service.impl.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public Result<ChatResponse> chat(@RequestBody ChatRequest request) {
        return Result.success(chatService.chat(request.sessionId(), request.message()));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatRequest request) {
        return chatService.stream(request.sessionId(), request.message());
    }

    @GetMapping("/{sessionId}/messages")
    public Result<ChatHistoryResponse> history(@PathVariable String sessionId) {
        return Result.success(chatService.history(sessionId));
    }

    @GetMapping("/sessions")
    public Result<ChatSessionListResponse> sessions() {
        return Result.success(chatService.sessions());
    }
}
