package com.ticketmind.mcpserver.model;

public record PriceInfo(
        String seatName,
        String shortName,
        String seatTypeCode,
        String num,
        String price,
        String discount) {
}
