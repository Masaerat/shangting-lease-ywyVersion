package com.atguigu.lease.common.login;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SysLoginUser {
    private Long userId;
    private String username;
    private Long type;

}
