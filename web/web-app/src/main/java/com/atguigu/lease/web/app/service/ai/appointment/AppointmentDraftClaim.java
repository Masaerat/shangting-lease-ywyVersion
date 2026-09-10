package com.atguigu.lease.web.app.service.ai.appointment;

public record AppointmentDraftClaim(Status status, AppointmentDraft draft) {

    public enum Status {
        CLAIMED,
        PROCESSING,
        MISSING
    }

    public static AppointmentDraftClaim claimed(AppointmentDraft draft) {
        return new AppointmentDraftClaim(Status.CLAIMED, draft);
    }

    public static AppointmentDraftClaim processing() {
        return new AppointmentDraftClaim(Status.PROCESSING, null);
    }

    public static AppointmentDraftClaim missing() {
        return new AppointmentDraftClaim(Status.MISSING, null);
    }
}
