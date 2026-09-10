package com.atguigu.lease.web.app.vo.ai.appointment;

public record AppointmentConfirmResponse(Long appointmentId, boolean idempotentReplay) {
}
