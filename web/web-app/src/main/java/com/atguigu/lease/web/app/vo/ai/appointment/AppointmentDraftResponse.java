package com.atguigu.lease.web.app.vo.ai.appointment;

import java.time.Instant;
import java.time.LocalDateTime;

public record AppointmentDraftResponse(
        String confirmationToken,
        Instant expiresAt,
        Long roomId,
        Long apartmentId,
        String name,
        String phone,
        LocalDateTime appointmentTime,
        String additionalInfo) {
}
