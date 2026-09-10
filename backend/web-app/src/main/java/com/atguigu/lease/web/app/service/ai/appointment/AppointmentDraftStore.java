package com.atguigu.lease.web.app.service.ai.appointment;

import java.time.Instant;

public interface AppointmentDraftStore {

    void save(Long userId, String token, AppointmentDraft draft, Instant expiresAt);

    AppointmentDraftClaim claim(Long userId, String token);

    void complete(Long userId, String token);

    void release(Long userId, String token);
}
