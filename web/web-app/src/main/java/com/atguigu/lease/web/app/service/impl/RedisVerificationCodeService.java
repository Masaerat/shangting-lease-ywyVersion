package com.atguigu.lease.web.app.service.impl;

import com.atguigu.lease.common.constant.RedisConstant;
import com.atguigu.lease.common.exception.LeaseException;
import com.atguigu.lease.common.result.ResultCodeEnum;
import com.atguigu.lease.common.utils.VerifyCodeUtil;
import com.atguigu.lease.web.app.service.SmsService;
import com.atguigu.lease.web.app.service.VerificationCodeService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(prefix = "app.demo-login", name = "enabled", havingValue = "false", matchIfMissing = true)
public class RedisVerificationCodeService implements VerificationCodeService {

    private final StringRedisTemplate redisTemplate;
    private final SmsService smsService;

    public RedisVerificationCodeService(StringRedisTemplate redisTemplate, SmsService smsService) {
        this.redisTemplate = redisTemplate;
        this.smsService = smsService;
    }

    @Override
    public void issue(String phone) {
        String key = RedisConstant.APP_LOGIN_PREFIX + phone;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            Long expire = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            if (expire != null
                    && RedisConstant.APP_LOGIN_CODE_TTL_SEC - expire < RedisConstant.APP_LOGIN_CODE_RESEND_TIME_SEC) {
                throw new LeaseException(ResultCodeEnum.APP_SEND_SMS_TOO_OFTEN);
            }
        }

        String code = VerifyCodeUtil.getVerifyCode(6);
        smsService.sendCode(phone, code);
        redisTemplate.opsForValue().set(
                key,
                code,
                RedisConstant.APP_LOGIN_CODE_TTL_SEC,
                TimeUnit.SECONDS);
    }

    @Override
    public boolean verify(String phone, String submittedCode) {
        String storedCode = redisTemplate.opsForValue().get(RedisConstant.APP_LOGIN_PREFIX + phone);
        if (storedCode == null) {
            throw new LeaseException(ResultCodeEnum.APP_LOGIN_CODE_EXPIRED);
        }
        if (!storedCode.equals(submittedCode)) {
            throw new LeaseException(ResultCodeEnum.APP_LOGIN_CODE_ERROR);
        }
        return true;
    }
}
