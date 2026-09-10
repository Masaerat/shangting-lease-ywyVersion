package com.atguigu.lease.common.constant;

/**
 * AI 模块 Redis key 常量。
 */
public class AiRedisConstant {

    private AiRedisConstant() {}

    /** 会话历史 key 前缀:ai:chat:history:{conversationId} */
    public static final String CHAT_HISTORY_PREFIX = "ai:chat:history:";

    /** 会话历史 TTL(秒),默认 2 小时 */
    public static final long CHAT_HISTORY_TTL_SEC = 2 * 60 * 60;
}
