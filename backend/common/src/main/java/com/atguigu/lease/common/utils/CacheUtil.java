package com.atguigu.lease.common.utils;

import com.atguigu.lease.common.constant.RedisConstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 缓存工具类
 */
@Component
public class CacheUtil {

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 获取缓存（自动处理空值）
     * @param key 缓存key
     * @param clazz 返回类型
     * @return 缓存对象，如果不存在或为空值标记返回null
     */
    public <T> T get(String key, Class<T> clazz) {
        String json = redisTemplate.opsForValue().get(key);

        if (json == null) {
            return null;
        }
        //如果是空值标记，返回null
        if (RedisConstant.CACHE_NULL_VALUE.equals(json)) {
            return null;
        }
        return JsonUtil.parseObject(json, clazz);
    }

    /**
     * 设置缓存
     * @param key 缓存key
     * @param value 缓存值（如果为null，会缓存空值标记防止穿透）
     * @param timeout 过期时间
     * @param unit 时间单位
     */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        if (value == null) {
            //防止缓存穿透，缓存空值
            redisTemplate.opsForValue().set(key, RedisConstant.CACHE_NULL_VALUE,
                    RedisConstant.CACHE_NULL_TTL_SEC, TimeUnit.SECONDS);
        } else {
            String json = JsonUtil.toJsonString(value);
            redisTemplate.opsForValue().set(key, json, timeout, unit);
        }
    }

    /**
     * 删除缓存
     */
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    /**
     * 批量删除缓存（按前缀）
     */
    public void deleteByPrefix(String prefix) {
        Set<String> keys = redisTemplate.keys(prefix + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
