package com.atguigu.lease.notification;

import java.time.LocalDateTime;

public record UserNotification(Long id, Long appointmentId, String type, String content,
                               LocalDateTime createdAt, LocalDateTime readAt) {
}
