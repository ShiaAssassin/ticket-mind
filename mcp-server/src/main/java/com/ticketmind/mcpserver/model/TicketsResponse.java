package com.ticketmind.mcpserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TicketsResponse(
        List<TicketInfo> tickets,
        String message,
        @JsonIgnore int httpStatus,
        @JsonIgnore String error) {

    public static TicketsResponse success(List<TicketInfo> tickets) {
        return new TicketsResponse(tickets, null, 200, null);
    }

    public static TicketsResponse empty(String message) {
        return new TicketsResponse(List.of(), message, 200, null);
    }

    public static TicketsResponse failure(int httpStatus, String error, String message) {
        return new TicketsResponse(null, message, httpStatus, error);
    }
}
