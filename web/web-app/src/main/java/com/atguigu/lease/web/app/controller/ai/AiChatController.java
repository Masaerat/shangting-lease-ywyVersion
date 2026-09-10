package com.atguigu.lease.web.app.controller.ai;

import com.atguigu.lease.web.app.service.ai.RentalChatService;
import com.atguigu.lease.web.app.vo.ai.ChatRequestVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "APP-AI对话")
@RestController
@RequestMapping("/app/ai")
public class AiChatController {

    @Autowired
    private RentalChatService rentalChatService;

    @Operation(summary = "AI 选房对话(SSE 流式)")
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequestVo request) {
        SseEmitter emitter = new SseEmitter(0L); // 不超时
        rentalChatService.chat(request, emitter);
        return emitter;
    }
}
