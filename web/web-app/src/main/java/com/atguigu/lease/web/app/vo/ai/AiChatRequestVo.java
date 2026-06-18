package com.atguigu.lease.web.app.vo.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "AI租房顾问对话请求")
public class AiChatRequestVo {

    @Schema(description = "会话Id，前端可复用同一个Id承载多轮对话")
    private String sessionId;

    @Schema(description = "用户消息")
    private String message;

    @Schema(description = "可选租房偏好")
    private AiPreferenceVo preferences;
}
