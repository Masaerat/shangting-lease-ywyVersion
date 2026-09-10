package com.atguigu.lease.web.app.vo.ai;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AiChatMetaVo {

    private String mode;
    private String conversationId;
    private String provider;
    private String traceId;

    public AiChatMetaVo(String mode, String conversationId) {
        this(mode, conversationId, null, null);
    }

    public AiChatMetaVo(String mode, String conversationId, String provider, String traceId) {
        this.mode = mode;
        this.conversationId = conversationId;
        this.provider = provider;
        this.traceId = traceId;
    }
}
