package com.atguigu.lease.web.app.service.ai.impl;

import com.atguigu.lease.web.app.service.ai.support.AiChatRequestValidator;
import com.atguigu.lease.web.app.service.ai.support.RentalIntentParser;
import com.atguigu.lease.web.app.vo.ai.AiChatRequestVo;
import com.atguigu.lease.web.app.vo.ai.AiChatResponseVo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiRentalAgentServiceImplTest {

    private final AiRentalAgentServiceImpl service = new AiRentalAgentServiceImpl(
            new AiChatRequestValidator(),
            new RentalIntentParser(),
            new InMemoryRentalKnowledgeService(),
            new FakeRentalRoomToolService()
    );

    @Test
    void chatHandlesMixedRoomSearchAndKnowledgeQuestion() {
        AiChatRequestVo requestVo = new AiChatRequestVo();
        requestVo.setSessionId("s-1");
        requestVo.setMessage("帮我找 2000 左右的房子，并说明押金怎么退");

        AiChatResponseVo responseVo = service.chat(requestVo);

        assertEquals("s-1", responseVo.getSessionId());
        assertFalse(responseVo.getRecommendedRooms().isEmpty());
        assertFalse(responseVo.getCitations().isEmpty());
        assertTrue(responseVo.getAnswer().contains("押金"));
    }
}
