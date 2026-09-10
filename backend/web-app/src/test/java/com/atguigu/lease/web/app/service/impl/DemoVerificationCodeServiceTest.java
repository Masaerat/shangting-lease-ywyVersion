package com.atguigu.lease.web.app.service.impl;

import com.atguigu.lease.web.app.config.DemoLoginProperties;
import com.atguigu.lease.web.app.service.SmsService;
import com.atguigu.lease.web.app.service.VerificationCodeService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DemoVerificationCodeServiceTest {

    @Test
    void fixedCodeIsLimitedToConfiguredDemoPhone() {
        var service = new DemoVerificationCodeService("13800000000", "888888");

        assertThat(service.verify("13800000000", "888888")).isTrue();
        assertThat(service.verify("13900000000", "888888")).isFalse();
        assertThat(service.verify("13800000000", "123456")).isFalse();
    }

    @Test
    void demoPropertySelectsExactlyOneVerificationStrategy() {
        contextRunner()
                .withPropertyValues(
                        "app.demo-login.enabled=true",
                        "app.demo-login.phone=13800000000",
                        "app.demo-login.code=888888")
                .run(context -> {
                    assertThat(context).hasSingleBean(VerificationCodeService.class);
                    assertThat(context).hasSingleBean(DemoVerificationCodeService.class);
                    assertThat(context).doesNotHaveBean(RedisVerificationCodeService.class);
                });

        contextRunner()
                .withPropertyValues("app.demo-login.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(VerificationCodeService.class);
                    assertThat(context).hasSingleBean(RedisVerificationCodeService.class);
                    assertThat(context).doesNotHaveBean(DemoVerificationCodeService.class);
                });
    }

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(VerificationStrategyConfiguration.class)
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean(SmsService.class, () -> mock(SmsService.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Import({DemoLoginProperties.class, DemoVerificationCodeService.class, RedisVerificationCodeService.class})
    static class VerificationStrategyConfiguration {
    }
}
