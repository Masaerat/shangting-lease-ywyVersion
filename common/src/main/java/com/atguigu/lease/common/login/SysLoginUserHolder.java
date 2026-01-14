package com.atguigu.lease.common.login;

public class SysLoginUserHolder {

    public static ThreadLocal<SysLoginUser> threadLocal = new ThreadLocal<>();//threadlocal

    public static void setSysLoginUser(SysLoginUser sysLoginUser) {
        threadLocal.set(sysLoginUser);
    }

    public static SysLoginUser getSysLoginUser() {
        return threadLocal.get();
    }

    public static void clear() {
        threadLocal.remove();
    }


}
