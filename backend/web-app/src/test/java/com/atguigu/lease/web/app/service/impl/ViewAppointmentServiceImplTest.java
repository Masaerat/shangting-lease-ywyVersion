package com.atguigu.lease.web.app.service.impl;

import com.atguigu.lease.model.entity.AppointmentEventOutbox;
import com.atguigu.lease.model.entity.ViewAppointment;
import com.atguigu.lease.model.enums.AppointmentStatus;
import com.atguigu.lease.web.app.mapper.AppointmentEventOutboxMapper;
import com.atguigu.lease.web.app.mapper.ViewAppointmentMapper;
import com.atguigu.lease.web.app.service.ApartmentInfoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViewAppointmentServiceImplTest {

    @Mock private ViewAppointmentMapper appointmentMapper;
    @Mock private ApartmentInfoService apartmentInfoService;
    @Mock private AppointmentEventOutboxMapper outboxMapper;

    @Test
    void saveWritesAppointmentAndOutboxWithoutCallingBroker() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-05T03:00:00Z"), ZoneOffset.UTC);
        ViewAppointmentServiceImpl service = new ViewAppointmentServiceImpl(
                appointmentMapper, apartmentInfoService, outboxMapper, clock);
        when(appointmentMapper.insert(any(ViewAppointment.class))).thenAnswer(invocation -> {
            ViewAppointment appointment = invocation.getArgument(0);
            appointment.setId(101L);
            return 1;
        });
        ViewAppointment appointment = appointment();

        boolean saved = service.saveWithMessage(appointment);

        assertThat(saved).isTrue();
        assertThat(appointment.getAppointmentStatus()).isEqualTo(AppointmentStatus.WAITING);
        ArgumentCaptor<AppointmentEventOutbox> outbox = ArgumentCaptor.forClass(AppointmentEventOutbox.class);
        verify(outboxMapper).insert(outbox.capture());
        assertThat(outbox.getValue().getAggregateId()).isEqualTo(101L);
        assertThat(outbox.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(outbox.getValue().getPayloadJson()).contains("\"appointmentId\":101");
    }

    private ViewAppointment appointment() {
        ViewAppointment appointment = new ViewAppointment();
        appointment.setUserId(990001L);
        appointment.setApartmentId(920001L);
        appointment.setName("演示用户");
        appointment.setPhone("13800000000");
        appointment.setAppointmentTime(new Date(1785986400000L));
        return appointment;
    }
}
