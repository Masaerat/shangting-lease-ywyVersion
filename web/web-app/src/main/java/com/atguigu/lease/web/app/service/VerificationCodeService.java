package com.atguigu.lease.web.app.service;

public interface VerificationCodeService {

    void issue(String phone);

    boolean verify(String phone, String code);
}
