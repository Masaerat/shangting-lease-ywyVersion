package com.atguigu.lease.web.app.service.ai.appointment;

import com.atguigu.lease.common.exception.LeaseException;
import com.atguigu.lease.model.entity.RoomInfo;
import com.atguigu.lease.model.enums.ReleaseStatus;
import com.atguigu.lease.web.app.mapper.RoomInfoMapper;
import com.atguigu.lease.web.app.vo.ai.appointment.AppointmentDraftRequest;
import com.atguigu.lease.web.app.vo.ai.appointment.AppointmentDraftResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentDraftServiceTest {

    private static final Long USER_ID = 960001L;
    private static final Long ROOM_ID = 930001L;
    private static final Instant NOW = Instant.parse("2026-08-05T02:00:00Z");

    @Mock
    private AppointmentDraftStore draftStore;

    @Mock
    private RoomInfoMapper roomInfoMapper;

    private AppointmentDraftService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new AppointmentDraftService(draftStore, roomInfoMapper, clock, () -> "fixed-token");
    }

    @Test
    void createsTenMinuteDraftForReleasedRoomWithoutPersistingAnAppointment() {
        when(roomInfoMapper.selectById(ROOM_ID)).thenReturn(room(ReleaseStatus.RELEASED));
        AppointmentDraftRequest request = validRequest();

        AppointmentDraftResponse response = service.create(USER_ID, request);

        assertThat(response.confirmationToken()).isEqualTo("fixed-token");
        assertThat(response.expiresAt()).isEqualTo(NOW.plusSeconds(600));
        assertThat(response.roomId()).isEqualTo(ROOM_ID);
        ArgumentCaptor<AppointmentDraft> draft = ArgumentCaptor.forClass(AppointmentDraft.class);
        verify(draftStore).save(eq(USER_ID), eq("fixed-token"), draft.capture(), eq(NOW.plusSeconds(600)));
        assertThat(draft.getValue().apartmentId()).isEqualTo(920001L);
        verify(roomInfoMapper).selectById(ROOM_ID);
    }

    @Test
    void rejectsInvalidPhone() {
        AppointmentDraftRequest request = validRequest();
        request.setPhone("12345");

        assertThatThrownBy(() -> service.create(USER_ID, request))
                .isInstanceOf(LeaseException.class)
                .hasMessage("请输入有效的手机号码");
    }

    @Test
    void rejectsPastAppointmentTime() {
        AppointmentDraftRequest request = validRequest();
        request.setAppointmentTime(LocalDateTime.ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC));

        assertThatThrownBy(() -> service.create(USER_ID, request))
                .isInstanceOf(LeaseException.class)
                .hasMessage("预约时间必须晚于当前时间");
    }

    @Test
    void rejectsUnavailableRoom() {
        when(roomInfoMapper.selectById(ROOM_ID)).thenReturn(room(ReleaseStatus.NOT_RELEASED));

        assertThatThrownBy(() -> service.create(USER_ID, validRequest()))
                .isInstanceOf(LeaseException.class)
                .hasMessage("该房间当前不可预约");
    }

    private AppointmentDraftRequest validRequest() {
        AppointmentDraftRequest request = new AppointmentDraftRequest();
        request.setRoomId(ROOM_ID);
        request.setName("演示用户");
        request.setPhone("13800000000");
        request.setAppointmentTime(LocalDateTime.ofInstant(NOW.plusSeconds(3600), ZoneOffset.UTC));
        request.setAdditionalInfo("希望下午看房");
        return request;
    }

    private RoomInfo room(ReleaseStatus status) {
        RoomInfo room = new RoomInfo();
        room.setId(ROOM_ID);
        room.setApartmentId(920001L);
        room.setIsRelease(status);
        return room;
    }
}
