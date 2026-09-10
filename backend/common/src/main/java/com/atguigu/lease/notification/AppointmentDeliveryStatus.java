package com.atguigu.lease.notification;

import java.time.LocalDateTime;

/** DELIVERED means persisted in-app notification, never SMS or email delivery. */
public record AppointmentDeliveryStatus(Long appointmentId, Long roomId, Integer appointmentStatus,
                                        LocalDateTime appointmentTime, String deliveryStatus,
                                        Long notificationId) {
}
