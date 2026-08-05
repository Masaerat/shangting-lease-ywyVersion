package com.atguigu.lease.web.app.service.ai.appointment;

import com.atguigu.lease.common.exception.LeaseException;
import com.atguigu.lease.common.result.ResultCodeEnum;
import com.atguigu.lease.model.entity.RoomInfo;
import com.atguigu.lease.model.enums.ReleaseStatus;
import com.atguigu.lease.web.app.mapper.RoomInfoMapper;
import com.atguigu.lease.web.app.vo.ai.appointment.AppointmentDraftRequest;
import com.atguigu.lease.web.app.vo.ai.appointment.AppointmentDraftResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.function.Supplier;

@Service
public class AppointmentDraftService {

    private static final Duration DRAFT_TTL = Duration.ofMinutes(10);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AppointmentDraftStore draftStore;
    private final RoomInfoMapper roomInfoMapper;
    private final Clock clock;
    private final Supplier<String> tokenSupplier;

    @Autowired
    public AppointmentDraftService(AppointmentDraftStore draftStore, RoomInfoMapper roomInfoMapper) {
        this(draftStore, roomInfoMapper, Clock.systemDefaultZone(), AppointmentDraftService::newToken);
    }

    AppointmentDraftService(AppointmentDraftStore draftStore, RoomInfoMapper roomInfoMapper,
                            Clock clock, Supplier<String> tokenSupplier) {
        this.draftStore = draftStore;
        this.roomInfoMapper = roomInfoMapper;
        this.clock = clock;
        this.tokenSupplier = tokenSupplier;
    }

    public AppointmentDraftResponse create(Long userId, AppointmentDraftRequest request) {
        validate(userId, request);
        RoomInfo room = roomInfoMapper.selectById(request.getRoomId());
        if (room == null || room.getIsRelease() != ReleaseStatus.RELEASED) {
            throw error("该房间当前不可预约");
        }

        String token = tokenSupplier.get();
        Instant expiresAt = clock.instant().plus(DRAFT_TTL);
        AppointmentDraft draft = new AppointmentDraft(
                userId,
                room.getId(),
                room.getApartmentId(),
                request.getName().trim(),
                request.getPhone().trim(),
                request.getAppointmentTime(),
                trimToNull(request.getAdditionalInfo()));
        draftStore.save(userId, token, draft, expiresAt);
        return new AppointmentDraftResponse(token, expiresAt, room.getId(), room.getApartmentId(),
                draft.name(), draft.phone(), draft.appointmentTime(), draft.additionalInfo());
    }

    private void validate(Long userId, AppointmentDraftRequest request) {
        if (userId == null || request == null || request.getRoomId() == null) {
            throw error("预约信息不完整");
        }
        if (request.getName() == null || request.getName().isBlank()
                || request.getName().trim().length() > 16) {
            throw error("请输入有效的联系人姓名");
        }
        if (request.getPhone() == null || !request.getPhone().trim().matches("^1[3-9]\\d{9}$")) {
            throw error("请输入有效的手机号码");
        }
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
        if (request.getAppointmentTime() == null || !request.getAppointmentTime().isAfter(now)) {
            throw error("预约时间必须晚于当前时间");
        }
        if (request.getAdditionalInfo() != null && request.getAdditionalInfo().length() > 255) {
            throw error("备注不能超过255个字符");
        }
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static LeaseException error(String message) {
        return new LeaseException(message, ResultCodeEnum.PARAM_ERROR.getCode());
    }
}
