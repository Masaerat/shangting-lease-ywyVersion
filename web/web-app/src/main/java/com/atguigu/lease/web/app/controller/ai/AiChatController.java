package com.atguigu.lease.web.app.controller.ai;

import com.atguigu.lease.common.result.Result;
import com.atguigu.lease.web.app.service.ai.AiRentalAgentService;
import com.atguigu.lease.web.app.vo.ai.AiChatRequestVo;
import com.atguigu.lease.web.app.vo.ai.AiChatResponseVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI租房顾问")
@RestController
@RequestMapping("/app/ai")
public class AiChatController {

    private final AiRentalAgentService aiRentalAgentService;

    public AiChatController(AiRentalAgentService aiRentalAgentService) {
        this.aiRentalAgentService = aiRentalAgentService;
    }

    @Operation(summary = "AI租房顾问对话")
    @PostMapping("chat")
    public Result<AiChatResponseVo> chat(@RequestBody AiChatRequestVo requestVo) {
        return Result.ok(aiRentalAgentService.chat(requestVo));
    }
}
