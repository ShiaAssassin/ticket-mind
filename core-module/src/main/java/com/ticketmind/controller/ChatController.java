package com.ticketmind.controller;

import com.ticketmind.common.Result;
import com.ticketmind.model.dto.ChatHistoryResponse;
import com.ticketmind.model.dto.ChatRequest;
import com.ticketmind.model.dto.ChatResponse;
import com.ticketmind.model.dto.ChatSessionListResponse;
import com.ticketmind.service.impl.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<Result<ChatResponse>> chat(@RequestBody ChatRequest request) {
        return ResponseEntity.ok(Result.success(chatService.chat(request.sessionId(), request.message())));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(@RequestBody ChatRequest request) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(chatService.stream(request.sessionId(), request.message()));
    }

    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<Result<ChatHistoryResponse>> history(@PathVariable Long sessionId) {
        return ResponseEntity.ok(Result.success(chatService.history(sessionId)));
    }

    @GetMapping("/sessions")
    public ResponseEntity<Result<ChatSessionListResponse>> sessions() {
        return ResponseEntity.ok(Result.success(chatService.sessions()));
    }
}
