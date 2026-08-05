package com.atguigu.lease.web.app.service.ai.appointment;

import com.atguigu.lease.common.exception.LeaseException;
import com.atguigu.lease.model.entity.AiAppointmentIdempotency;
import com.atguigu.lease.model.entity.AppointmentEventOutbox;
import com.atguigu.lease.model.entity.RoomInfo;
import com.atguigu.lease.model.entity.ViewAppointment;
import com.atguigu.lease.model.enums.ReleaseStatus;
import com.atguigu.lease.web.app.mapper.AiAppointmentIdempotencyMapper;
import com.atguigu.lease.web.app.mapper.AppointmentEventOutboxMapper;
import com.atguigu.lease.web.app.mapper.RoomInfoMapper;
import com.atguigu.lease.web.app.mapper.ViewAppointmentMapper;
import com.atguigu.lease.web.app.vo.ai.appointment.AppointmentConfirmResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class AppointmentConfirmationServiceTest {

    private static final Long USER_ID = 960001L;
    private static final Long ROOM_ID = 930001L;
    private static final String TOKEN = "confirmation-token";

    @Mock private AppointmentDraftStore draftStore;
    @Mock private RoomInfoMapper roomInfoMapper;
    @Mock private ViewAppointmentMapper appointmentMapper;
    @Mock private AiAppointmentIdempotencyMapper idempotencyMapper;
    @Mock private AppointmentEventOutboxMapper outboxMapper;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private TransactionStatus transactionStatus;

    private AppointmentConfirmationService service;

    @BeforeEach
    void setUp() {
        service = new AppointmentConfirmationService(draftStore, roomInfoMapper, appointmentMapper,
                idempotencyMapper, outboxMapper, transactionTemplate,
                Clock.fixed(Instant.parse("2026-08-05T02:00:00Z"), ZoneOffset.UTC));
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        });
    }

    @Test
    void confirmationWritesAppointmentIdempotencyAndOutboxTogether() {
        AppointmentDraft draft = draft();
        when(idempotencyMapper.selectByUserAndTokenHash(anyLong(), any())).thenReturn(null);
        when(draftStore.claim(USER_ID, TOKEN)).thenReturn(AppointmentDraftClaim.claimed(draft));
        when(roomInfoMapper.selectReleasedByIdForUpdate(ROOM_ID)).thenReturn(room());
        when(appointmentMapper.countActiveByUserAndRoom(USER_ID, ROOM_ID)).thenReturn(0L);
        when(appointmentMapper.insert(any(ViewAppointment.class))).thenAnswer(invocation -> {
            ViewAppointment entity = invocation.getArgument(0);
            entity.setId(101L);
            return 1;
        });

        AppointmentConfirmResponse response = service.confirm(USER_ID, TOKEN);

        assertThat(response.appointmentId()).isEqualTo(101L);
        assertThat(response.idempotentReplay()).isFalse();
        verify(idempotencyMapper).insert(any(AiAppointmentIdempotency.class));
        ArgumentCaptor<AppointmentEventOutbox> outbox = ArgumentCaptor.forClass(AppointmentEventOutbox.class);
        verify(outboxMapper).insert(outbox.capture());
        assertThat(outbox.getValue().getAggregateId()).isEqualTo(101L);
        assertThat(outbox.getValue().getPayloadJson()).contains("\"roomId\":930001");
        verify(draftStore).complete(USER_ID, TOKEN);
    }

    @Test
    void replayReturnsExistingAppointmentWithoutCreatingAnotherOne() {
        AiAppointmentIdempotency existing = new AiAppointmentIdempotency();
        existing.setAppointmentId(101L);
        when(idempotencyMapper.selectByUserAndTokenHash(anyLong(), any())).thenReturn(existing);

        AppointmentConfirmResponse response = service.confirm(USER_ID, TOKEN);

        assertThat(response.appointmentId()).isEqualTo(101L);
        assertThat(response.idempotentReplay()).isTrue();
        verify(draftStore, never()).claim(anyLong(), any());
        verify(appointmentMapper, never()).insert(any(ViewAppointment.class));
    }

    @Test
    void committedAppointmentStillReturnsSuccessWhenDraftCleanupFails() {
        when(idempotencyMapper.selectByUserAndTokenHash(anyLong(), any())).thenReturn(null);
        when(draftStore.claim(USER_ID, TOKEN)).thenReturn(AppointmentDraftClaim.claimed(draft()));
        when(roomInfoMapper.selectReleasedByIdForUpdate(ROOM_ID)).thenReturn(room());
        when(appointmentMapper.countActiveByUserAndRoom(USER_ID, ROOM_ID)).thenReturn(0L);
        when(appointmentMapper.insert(any(ViewAppointment.class))).thenAnswer(invocation -> {
            ViewAppointment entity = invocation.getArgument(0);
            entity.setId(101L);
            return 1;
        });
        doThrow(new IllegalStateException("redis unavailable"))
                .when(draftStore).complete(USER_ID, TOKEN);

        AppointmentConfirmResponse response = service.confirm(USER_ID, TOKEN);

        assertThat(response.appointmentId()).isEqualTo(101L);
        assertThat(response.idempotentReplay()).isFalse();
        verify(draftStore, never()).release(USER_ID, TOKEN);
    }

    @Test
    void rejectsExpiredOrForeignToken() {
        when(idempotencyMapper.selectByUserAndTokenHash(anyLong(), any())).thenReturn(null);
        when(draftStore.claim(USER_ID, TOKEN)).thenReturn(AppointmentDraftClaim.missing());

        assertThatThrownBy(() -> service.confirm(USER_ID, TOKEN))
                .isInstanceOf(LeaseException.class)
                .hasMessage("确认令牌无效或已过期");
    }

    @Test
    void rejectsRoomThatBecameUnavailable() {
        when(idempotencyMapper.selectByUserAndTokenHash(anyLong(), any())).thenReturn(null);
        when(draftStore.claim(USER_ID, TOKEN)).thenReturn(AppointmentDraftClaim.claimed(draft()));
        when(roomInfoMapper.selectReleasedByIdForUpdate(ROOM_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.confirm(USER_ID, TOKEN))
                .isInstanceOf(LeaseException.class)
                .hasMessage("该房间当前不可预约");
        verify(draftStore).release(USER_ID, TOKEN);
    }

    @Test
    void rejectsDuplicateActiveAppointment() {
        when(idempotencyMapper.selectByUserAndTokenHash(anyLong(), any())).thenReturn(null);
        when(draftStore.claim(USER_ID, TOKEN)).thenReturn(AppointmentDraftClaim.claimed(draft()));
        when(roomInfoMapper.selectReleasedByIdForUpdate(ROOM_ID)).thenReturn(room());
        when(appointmentMapper.countActiveByUserAndRoom(USER_ID, ROOM_ID)).thenReturn(1L);

        assertThatThrownBy(() -> service.confirm(USER_ID, TOKEN))
                .isInstanceOf(LeaseException.class)
                .hasMessage("你已预约过该房间");
        verify(draftStore).release(USER_ID, TOKEN);
    }

    @Test
    void revalidatesAppointmentTimeDuringConfirmation() {
        AppointmentDraft expiredDraft = new AppointmentDraft(
                USER_ID, ROOM_ID, 920001L, "演示用户", "13800000000",
                LocalDateTime.of(2026, 8, 5, 1, 59), null);
        when(idempotencyMapper.selectByUserAndTokenHash(anyLong(), any())).thenReturn(null);
        when(draftStore.claim(USER_ID, TOKEN)).thenReturn(AppointmentDraftClaim.claimed(expiredDraft));

        assertThatThrownBy(() -> service.confirm(USER_ID, TOKEN))
                .isInstanceOf(LeaseException.class)
                .hasMessage("预约时间必须晚于当前时间");
        verify(draftStore).release(USER_ID, TOKEN);
        verify(roomInfoMapper, never()).selectReleasedByIdForUpdate(anyLong());
    }

    private AppointmentDraft draft() {
        return new AppointmentDraft(USER_ID, ROOM_ID, 920001L, "演示用户", "13800000000",
                LocalDateTime.of(2026, 8, 6, 14, 0), "希望下午看房");
    }

    private RoomInfo room() {
        RoomInfo room = new RoomInfo();
        room.setId(ROOM_ID);
        room.setApartmentId(920001L);
        room.setIsRelease(ReleaseStatus.RELEASED);
        return room;
    }
}
