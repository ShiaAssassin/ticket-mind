package com.ticketmind.agent.tool.registry;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class ExternalInfoTools {

    @Tool("搜索与出行相关的外部信息")
    public String searchExternalInfo(@P("搜索内容") String query) {
        // TODO 接入外部信息搜索能力，并返回可用于行程决策的结果。
        return null;
    }

    @Tool("查询出行城市天气")
    public String queryWeather(@P("城市") String city, @P("日期") String date) {
        // TODO 接入天气服务并返回指定日期的天气信息。
        return null;
    }

    @Tool("查询车站位置和进站信息")
    public String queryStationInfo(@P("车站名称") String stationName) {
        // TODO 接入车站信息服务并返回位置、进站和换乘提示。
        return null;
    }
}
