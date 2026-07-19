package com.ticketmind.mcpserver.model;

import java.util.List;

public record TicketInfo(
        String trainNo,
        String startTrainCode,
        String startTime,
        String arriveTime,
        String lishi,
        String fromStation,
        String toStation,
        String fromStationTelecode,
        String toStationTelecode,
        List<PriceInfo> prices,
        List<String> dwFlag) {
}
