package com.ticketmind.model.dto;

import com.ticketmind.model.entity.MessageRole;

public record MemoryMessage(MessageRole role, String content) {
}
