package com.ticketmind.agent.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class PlanTools {

    @Tool("规划出行方案")
    public String planTrip(@P("出发地") String departure,
                           @P("目的地") String destination,
                           @P("出发日期") String date,
                           @P("出行偏好") String preference) {
        // TODO 完成直达和中转换乘方案规划。
        return null;
    }

    @Tool("规划中转换乘方案")
    public String findTransferPlans(@P("出发地") String departure,
                                    @P("目的地") String destination,
                                    @P("出发日期") String date,
                                    @P("换乘偏好") String transferPreference) {
        // TODO 完成中转换乘路径搜索、筛选和排序。
        return null;
    }

    @Tool("创建余票监控任务")
    public String createMonitorTask(@P("监控条件") String criteria,
                                    @P("监控时间范围") String timeWindow,
                                    @P("有票后的动作") String action) {
        // TODO 完成监控任务创建、调度和策略保存。
        return null;
    }

    @Tool("查询余票监控任务")
    public String queryMonitorTask(@P("监控任务编号") String taskId) {
        // TODO 完成监控任务状态查询。
        return null;
    }

    @Tool("取消余票监控任务")
    public String cancelMonitorTask(@P("监控任务编号") String taskId) {
        // TODO 完成监控任务取消。
        return null;
    }
}
