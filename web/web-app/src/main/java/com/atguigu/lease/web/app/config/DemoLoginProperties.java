package com.atguigu.lease.web.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.demo-login")
public class DemoLoginProperties {

    private boolean enabled;
    private String phone = "13800000000";
    private String code = "888888";
}
