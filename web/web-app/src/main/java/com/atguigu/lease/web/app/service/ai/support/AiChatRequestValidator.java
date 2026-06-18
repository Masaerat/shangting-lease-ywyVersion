package com.atguigu.lease.web.app.service.ai.support;

import com.atguigu.lease.common.exception.LeaseException;
import com.atguigu.lease.common.result.ResultCodeEnum;
import com.atguigu.lease.web.app.vo.ai.AiChatRequestVo;
import org.springframework.stereotype.Component;

@Component
public class AiChatRequestValidator {

    private static final int MAX_MESSAGE_LENGTH = 1000;

    public void validate(AiChatRequestVo requestVo) {
        if (requestVo == null) {
            throw new LeaseException(ResultCodeEnum.PARAM_ERROR);
        }
        String message = requestVo.getMessage();
        if (message == null || message.trim().isEmpty()) {
            throw new LeaseException(ResultCodeEnum.PARAM_ERROR);
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new LeaseException(ResultCodeEnum.PARAM_ERROR);
        }
    }
}
