package com.ticketmind.agent.tool.registry;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class TicketInfoTools {

    @Tool("查询列车、余票、票价和时刻信息")
    public String queryTickets(@P("出发站") String departureStation,
                               @P("到达站") String arrivalStation,
                               @P("出发日期") String date,
                               @P("席别偏好") String seatType) {
        // TODO 接入票务查询服务并返回符合条件的车次信息。
        return null;
    }

    @Tool("查询指定车次详情")
    public String queryTrainDetail(@P("车次编号") String trainNumber,
                                   @P("出发日期") String date) {
        // TODO 完成指定车次经停站、时刻、席别和票价查询。
        return null;
    }

    @Tool("查询购票、退票和改签规则")
    public String queryTicketRules(@P("规则主题") String ruleTopic) {
        // TODO 接入票务规则数据并返回适用规则。
        return null;
    }
}
