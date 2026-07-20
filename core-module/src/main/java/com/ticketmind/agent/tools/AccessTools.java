package com.ticketmind.agent.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class AccessTools {

    @Tool("登录票务平台账号")
    public String login(@P("用户名") String username, @P("密码") String password) {
        // TODO 完成票务平台账号登录和登录态保存。
        return null;
    }

    @Tool("退出当前票务平台账号")
    public String logout() {
        // TODO 完成当前票务平台账号退出和登录态清理。
        return null;
    }

    @Tool("查询当前登录账号信息")
    public String getCurrentAccount() {
        // TODO 完成当前登录账号信息查询。
        return null;
    }

    @Tool("查询当前账号的常用乘车人")
    public String listPassengers() {
        // TODO 完成常用乘车人列表查询。
        return null;
    }

    @Tool("添加常用乘车人")
    public String addPassenger(@P("乘车人姓名") String name,
                               @P("证件类型") String idType,
                               @P("证件号码") String idNumber) {
        // TODO 完成常用乘车人新增和实名认证校验。
        return null;
    }
}
