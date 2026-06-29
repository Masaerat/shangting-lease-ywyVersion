package com.atguigu.lease.web.app.vo.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "AI租房顾问对话响应")
public class AiChatResponseVo {

    @Schema(description = "会话Id")
    private String sessionId;

    @Schema(description = "AI回答")
    private String answer;

    @Schema(description = "推荐房源")
    private List<AiRecommendedRoomVo> recommendedRooms;

    @Schema(description = "RAG引用来源")
    private List<AiCitationVo> citations;

    @Schema(description = "建议下一步动作")
    private List<String> suggestedActions;
}
