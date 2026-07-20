package com.ticketmind.agent.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class OrderTools {

    @Tool("提交票务订单")
    public String createOrder(@P("车次编号") String trainNumber,
                              @P("出发日期") String date,
                              @P("乘车人") String passengerNames,
                              @P("席别") String seatType) {
        // TODO 完成订单创建、锁座和订单结果返回。
        return null;
    }

    @Tool("提交候补购票订单")
    public String submitWaitlistOrder(@P("候补车次") String trainNumbers,
                                      @P("出发日期") String date,
                                      @P("乘车人") String passengerNames,
                                      @P("席别") String seatType) {
        // TODO 完成候补订单提交和候补策略保存。
        return null;
    }

    @Tool("查询订单状态")
    public String queryOrderStatus(@P("订单编号") String orderId) {
        // TODO 完成订单状态查询。
        return null;
    }

    @Tool("取消未完成订单")
    public String cancelOrder(@P("订单编号") String orderId) {
        // TODO 完成未支付订单取消。
        return null;
    }

    @Tool("支付订单")
    public String payOrder(@P("订单编号") String orderId) {
        // TODO 完成订单支付流程或生成支付提醒。
        return null;
    }

    @Tool("申请退票")
    public String refundOrder(@P("订单编号") String orderId,
                              @P("退票原因") String reason) {
        // TODO 完成退票申请和退款结果查询。
        return null;
    }

    @Tool("申请改签")
    public String changeOrder(@P("订单编号") String orderId,
                              @P("目标车次") String targetTrainNumber,
                              @P("目标日期") String targetDate) {
        // TODO 完成改签申请及目标车次无票时的后续监控。
        return null;
    }
}
