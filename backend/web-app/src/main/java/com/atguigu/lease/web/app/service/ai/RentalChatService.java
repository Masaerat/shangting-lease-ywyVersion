package com.atguigu.lease.web.app.service.ai;

import com.atguigu.lease.web.app.vo.ai.ChatRequestVo;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 租房 AI 对话:手动检索 + 多轮历史 + 流式回答。
 */
public interface RentalChatService {

    /** 流式对话:把 token / 引用 / 错误通过 SSE emitter 推送。 */
    void chat(ChatRequestVo request, SseEmitter emitter);
}
