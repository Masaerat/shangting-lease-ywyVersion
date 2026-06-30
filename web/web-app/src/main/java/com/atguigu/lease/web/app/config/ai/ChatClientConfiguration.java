package com.atguigu.lease.web.app.config.ai;

import com.atguigu.lease.web.app.tools.RoomSearchTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 租房顾问 ChatClient:系统提示 + 默认挂载 RoomSearchTool。
 * v1 不注册 QuestionAnswerAdvisor;检索上下文由 RentalChatService 按请求手动注入。
 */
@Configuration
public class ChatClientConfiguration {

    @Bean
    public ChatClient rentalChatClient(ChatClient.Builder builder, RoomSearchTool roomSearchTool) {
        String system = """
                你是一名专业、友善的租房顾问。根据给定的房源资料回答用户问题、推荐房源。
                规则:
                1) 只依据提供的参考资料与 searchRooms 工具的结果作答,不要编造不存在的房源。
                2) 推荐时给出房间号、租金与简短理由。
                3) 若用户给出明确的预算/位置/户型,优先调用 searchRooms 工具精确筛选。
                4) 不确定时如实告知。
                """;
        return builder.defaultSystem(system).defaultTools(roomSearchTool).build();
    }
}
