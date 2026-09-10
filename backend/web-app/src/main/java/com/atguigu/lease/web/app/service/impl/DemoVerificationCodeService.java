package com.atguigu.lease.web.app.service.impl;

import com.atguigu.lease.common.exception.LeaseException;
import com.atguigu.lease.common.result.ResultCodeEnum;
import com.atguigu.lease.web.app.config.DemoLoginProperties;
import com.atguigu.lease.web.app.service.VerificationCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.demo-login", name = "enabled", havingValue = "true")
public class DemoVerificationCodeService implements VerificationCodeService {

    private final String demoPhone;
    private final String demoCode;

    @Autowired
    public DemoVerificationCodeService(DemoLoginProperties properties) {
        this(properties.getPhone(), properties.getCode());
    }

    public DemoVerificationCodeService(String demoPhone, String demoCode) {
        this.demoPhone = demoPhone;
        this.demoCode = demoCode;
    }

    @Override
    public void issue(String phone) {
        if (!demoPhone.equals(phone)) {
            throw new LeaseException(ResultCodeEnum.APP_LOGIN_CODE_ERROR);
        }
    }

    @Override
    public boolean verify(String phone, String code) {
        return demoPhone.equals(phone) && demoCode.equals(code);
    }
}
