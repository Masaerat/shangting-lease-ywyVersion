package com.atguigu.lease.outbox;

public interface AppointmentEventSender {

    void send(OutboxEvent event);
}
