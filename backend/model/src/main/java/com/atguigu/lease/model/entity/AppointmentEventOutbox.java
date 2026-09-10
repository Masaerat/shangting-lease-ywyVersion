package com.atguigu.lease.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("appointment_event_outbox")
public class AppointmentEventOutbox {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long aggregateId;
    private String eventType;
    private String payloadJson;
    private String status;
    private Integer attempts;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime publishedAt;
    private String lastError;
    private LocalDateTime createdAt;
}
