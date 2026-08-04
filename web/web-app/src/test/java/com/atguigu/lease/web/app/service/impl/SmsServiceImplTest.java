package com.atguigu.lease.web.app.service.impl;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SmsServiceImplTest {

    @Test
    void sendsGeneratedCodeThroughAliyunClient() throws Exception {
        Client client = mock(Client.class);
        SmsServiceImpl service = new SmsServiceImpl(client);

        service.sendCode("13800000000", "456789");

        ArgumentCaptor<SendSmsRequest> request = ArgumentCaptor.forClass(SendSmsRequest.class);
        verify(client).sendSms(request.capture());
        assertThat(request.getValue().getPhoneNumbers()).isEqualTo("13800000000");
        assertThat(request.getValue().getTemplateParam()).isEqualTo("{\"code\":\"456789\"}");
    }
}
