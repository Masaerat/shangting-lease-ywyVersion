package com.atguigu.lease.web.app.service.ai;

import com.atguigu.lease.web.app.vo.ai.AiChatRequestVo;
import com.atguigu.lease.web.app.vo.ai.AiChatResponseVo;

public interface AiRentalAgentService {

    AiChatResponseVo chat(AiChatRequestVo requestVo);
}
