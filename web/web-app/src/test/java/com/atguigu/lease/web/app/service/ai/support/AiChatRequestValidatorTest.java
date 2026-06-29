package com.atguigu.lease.web.app.service.ai.support;

import com.atguigu.lease.common.exception.LeaseException;
import com.atguigu.lease.web.app.vo.ai.AiChatRequestVo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiChatRequestValidatorTest {

    private final AiChatRequestValidator validator = new AiChatRequestValidator();

    @Test
    void validateRejectsEmptyMessage() {
        AiChatRequestVo requestVo = new AiChatRequestVo();
        requestVo.setMessage(" ");

        assertThrows(LeaseException.class, () -> validator.validate(requestVo));
    }

    @Test
    void validateAcceptsNormalMessage() {
        AiChatRequestVo requestVo = new AiChatRequestVo();
        requestVo.setMessage("帮我找 2000 左右的房子");

        assertDoesNotThrow(() -> validator.validate(requestVo));
    }
}
