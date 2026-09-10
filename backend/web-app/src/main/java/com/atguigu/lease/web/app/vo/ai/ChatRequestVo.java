package com.atguigu.lease.web.app.vo.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "AI 对话请求")
public class ChatRequestVo {

    @Schema(description = "用户消息")
    private String message;

    @Schema(description = "会话ID,为空则服务端按用户生成")
    private String conversationId;
}
