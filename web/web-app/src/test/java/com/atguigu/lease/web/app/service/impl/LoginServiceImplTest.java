package com.atguigu.lease.web.app.service.impl;

import com.atguigu.lease.common.exception.LeaseException;
import com.atguigu.lease.common.result.ResultCodeEnum;
import com.atguigu.lease.model.entity.UserInfo;
import com.atguigu.lease.model.enums.BaseStatus;
import com.atguigu.lease.web.app.service.UserInfoService;
import com.atguigu.lease.web.app.service.VerificationCodeService;
import com.atguigu.lease.web.app.vo.user.LoginVo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginServiceImplTest {

    private final VerificationCodeService verificationCodeService = mock(VerificationCodeService.class);
    private final UserInfoService userInfoService = mock(UserInfoService.class);
    private final LoginServiceImpl service = new LoginServiceImpl(verificationCodeService, userInfoService);

    @Test
    void getSmsCodeDelegatesToVerificationStrategy() {
        service.getSMSCode("13800000000");

        verify(verificationCodeService).issue("13800000000");
    }

    @Test
    void rejectedCodeKeepsExistingLoginErrorCode() {
        var login = login("13800000000", "123456");
        when(verificationCodeService.verify(login.getPhone(), login.getCode())).thenReturn(false);

        assertThatThrownBy(() -> service.login(login))
                .isInstanceOfSatisfying(LeaseException.class,
                        error -> assertThat(error.getCode()).isEqualTo(ResultCodeEnum.APP_LOGIN_CODE_ERROR.getCode()));
    }

    @Test
    void acceptedCodeReturnsTokenForExistingUser() {
        var login = login("13800000000", "888888");
        var user = new UserInfo();
        user.setId(960001L);
        user.setPhone(login.getPhone());
        user.setStatus(BaseStatus.ENABLE);
        when(verificationCodeService.verify(login.getPhone(), login.getCode())).thenReturn(true);
        when(userInfoService.getOne(any())).thenReturn(user);

        assertThat(service.login(login)).isNotBlank();
    }

    private LoginVo login(String phone, String code) {
        var login = new LoginVo();
        login.setPhone(phone);
        login.setCode(code);
        return login;
    }
}
