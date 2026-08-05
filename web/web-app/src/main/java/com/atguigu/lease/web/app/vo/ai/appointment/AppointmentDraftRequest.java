package com.atguigu.lease.web.app.vo.ai.appointment;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AppointmentDraftRequest {

    private Long roomId;
    private String name;
    private String phone;
    private LocalDateTime appointmentTime;
    private String additionalInfo;
}
