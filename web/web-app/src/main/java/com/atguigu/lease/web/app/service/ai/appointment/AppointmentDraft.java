package com.atguigu.lease.web.app.service.ai.appointment;

import java.time.LocalDateTime;

public record AppointmentDraft(
        Long userId,
        Long roomId,
        Long apartmentId,
        String name,
        String phone,
        LocalDateTime appointmentTime,
        String additionalInfo) {
}
