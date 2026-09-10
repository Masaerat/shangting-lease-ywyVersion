package com.atguigu.lease.web.app.mapper;

import com.atguigu.lease.model.entity.AiAppointmentIdempotency;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

public interface AiAppointmentIdempotencyMapper extends BaseMapper<AiAppointmentIdempotency> {

    @Select("""
            SELECT id, user_id, token_hash, appointment_id, created_at
            FROM ai_appointment_idempotency
            WHERE user_id = #{userId} AND token_hash = #{tokenHash}
            LIMIT 1
            """)
    AiAppointmentIdempotency selectByUserAndTokenHash(Long userId, String tokenHash);
}
