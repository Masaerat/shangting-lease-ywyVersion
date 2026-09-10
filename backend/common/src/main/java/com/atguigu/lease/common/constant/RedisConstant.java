package com.atguigu.lease.common.constant;

/**
 * 为方便管理，可以将Reids相关的一些值定义为常量，例如key的前缀、TTL时长，内容如下。
 */
public class RedisConstant {
    //web-admin模块
    public static final String ADMIN_LOGIN_PREFIX = "admin:login:";
    public static final Integer ADMIN_LOGIN_CAPTCHA_TTL_SEC = 60;

    //web-app模块
    public static final String APP_LOGIN_PREFIX = "app:login:";
    public static final Integer APP_LOGIN_CODE_RESEND_TIME_SEC = 60;
    public static final Integer APP_LOGIN_CODE_TTL_SEC = 60 * 10;

    //房间详情缓存
    public static final String APP_ROOM_DETAIL_PREFIX = "app:room:detail:";
    public static final Integer APP_ROOM_DETAIL_TTL_SEC = 60 * 30; // 30分钟

    //公寓简要信息缓存
    public static final String APP_APARTMENT_ITEM_PREFIX = "app:apartment:item:";
    public static final Integer APP_APARTMENT_ITEM_TTL_SEC = 60 * 60; // 1小时

    //空值缓存（防止穿透）
    public static final String CACHE_NULL_VALUE = "NULL";
    public static final Integer CACHE_NULL_TTL_SEC = 60;
}
