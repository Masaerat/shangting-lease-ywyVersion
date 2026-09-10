package com.atguigu.lease.web.app.service.ai.appointment;

import com.atguigu.lease.common.exception.LeaseException;
import com.atguigu.lease.common.result.ResultCodeEnum;
import com.atguigu.lease.common.utils.JsonUtil;
import com.atguigu.lease.model.entity.AiAppointmentIdempotency;
import com.atguigu.lease.model.entity.AppointmentEventOutbox;
import com.atguigu.lease.model.entity.RoomInfo;
import com.atguigu.lease.model.entity.ViewAppointment;
import com.atguigu.lease.model.enums.AppointmentStatus;
import com.atguigu.lease.web.app.mapper.AiAppointmentIdempotencyMapper;
import com.atguigu.lease.web.app.mapper.AppointmentEventOutboxMapper;
import com.atguigu.lease.web.app.mapper.RoomInfoMapper;
import com.atguigu.lease.web.app.mapper.ViewAppointmentMapper;
import com.atguigu.lease.web.app.vo.ai.appointment.AppointmentConfirmResponse;
import org.springframework.dao.DuplicateKeyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class AppointmentConfirmationService {

    private static final int PROCESSING_RETRIES = 40;
    private static final Logger LOGGER = LoggerFactory.getLogger(AppointmentConfirmationService.class);

    private final AppointmentDraftStore draftStore;
    private final RoomInfoMapper roomInfoMapper;
    private final ViewAppointmentMapper appointmentMapper;
    private final AiAppointmentIdempotencyMapper idempotencyMapper;
    private final AppointmentEventOutboxMapper outboxMapper;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Autowired
    public AppointmentConfirmationService(AppointmentDraftStore draftStore, RoomInfoMapper roomInfoMapper,
                                          ViewAppointmentMapper appointmentMapper,
                                          AiAppointmentIdempotencyMapper idempotencyMapper,
                                          AppointmentEventOutboxMapper outboxMapper,
                                          TransactionTemplate transactionTemplate) {
        this(draftStore, roomInfoMapper, appointmentMapper, idempotencyMapper, outboxMapper,
                transactionTemplate, Clock.systemDefaultZone());
    }

    AppointmentConfirmationService(AppointmentDraftStore draftStore, RoomInfoMapper roomInfoMapper,
                                   ViewAppointmentMapper appointmentMapper,
                                   AiAppointmentIdempotencyMapper idempotencyMapper,
                                   AppointmentEventOutboxMapper outboxMapper,
                                   TransactionTemplate transactionTemplate, Clock clock) {
        this.draftStore = draftStore;
        this.roomInfoMapper = roomInfoMapper;
        this.appointmentMapper = appointmentMapper;
        this.idempotencyMapper = idempotencyMapper;
        this.outboxMapper = outboxMapper;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    public AppointmentConfirmResponse confirm(Long userId, String token) {
        if (userId == null || token == null || token.isBlank() || token.length() > 128) {
            throw error("确认令牌无效或已过期", ResultCodeEnum.PARAM_ERROR);
        }
        String tokenHash = sha256(token);
        AppointmentConfirmResponse replay = replay(userId, tokenHash);
        if (replay != null) {
            return replay;
        }

        AppointmentDraftClaim claim = draftStore.claim(userId, token);
        if (claim.status() == AppointmentDraftClaim.Status.MISSING) {
            throw error("确认令牌无效或已过期", ResultCodeEnum.PARAM_ERROR);
        }
        if (claim.status() == AppointmentDraftClaim.Status.PROCESSING) {
            return waitForReplay(userId, tokenHash);
        }

        AppointmentConfirmResponse response;
        try {
            try {
                response = transactionTemplate.execute(status ->
                        createAppointment(userId, tokenHash, claim.draft()));
                if (response == null) {
                    throw error("预约确认失败，请重试", ResultCodeEnum.SERVICE_ERROR);
                }
            } catch (DuplicateKeyException exception) {
                response = waitForReplay(userId, tokenHash);
            }
        } catch (RuntimeException exception) {
            releaseBestEffort(userId, token);
            throw exception;
        }
        completeBestEffort(userId, token);
        return response;
    }

    private AppointmentConfirmResponse createAppointment(Long userId, String tokenHash, AppointmentDraft draft) {
        if (!userId.equals(draft.userId())) {
            throw error("确认令牌无效或已过期", ResultCodeEnum.PARAM_ERROR);
        }
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
        if (draft.appointmentTime() == null || !draft.appointmentTime().isAfter(now)) {
            throw error("预约时间必须晚于当前时间", ResultCodeEnum.PARAM_ERROR);
        }
        RoomInfo room = roomInfoMapper.selectReleasedByIdForUpdate(draft.roomId());
        if (room == null || !Objects.equals(room.getApartmentId(), draft.apartmentId())) {
            throw error("该房间当前不可预约", ResultCodeEnum.DATA_ERROR);
        }
        if (appointmentMapper.countActiveByUserAndRoom(userId, draft.roomId()) > 0) {
            throw error("你已预约过该房间", ResultCodeEnum.REPEAT_SUBMIT);
        }

        ViewAppointment appointment = toAppointment(draft);
        appointmentMapper.insert(appointment);
        if (appointment.getId() == null) {
            throw error("预约确认失败，请重试", ResultCodeEnum.SERVICE_ERROR);
        }

        AppointmentEventOutbox outbox = new AppointmentEventOutbox();
        outbox.setAggregateId(appointment.getId());
        outbox.setEventType("APPOINTMENT_CREATED");
        outbox.setPayloadJson(payload(appointment));
        outbox.setStatus("PENDING");
        outbox.setAttempts(0);
        outbox.setNextAttemptAt(now);
        outbox.setCreatedAt(now);
        outboxMapper.insert(outbox);

        AiAppointmentIdempotency idempotency = new AiAppointmentIdempotency();
        idempotency.setUserId(userId);
        idempotency.setTokenHash(tokenHash);
        idempotency.setAppointmentId(appointment.getId());
        idempotency.setCreatedAt(now);
        idempotencyMapper.insert(idempotency);
        return new AppointmentConfirmResponse(appointment.getId(), false);
    }

    private AppointmentConfirmResponse replay(Long userId, String tokenHash) {
        AiAppointmentIdempotency existing = idempotencyMapper.selectByUserAndTokenHash(userId, tokenHash);
        return existing == null ? null : new AppointmentConfirmResponse(existing.getAppointmentId(), true);
    }

    private AppointmentConfirmResponse waitForReplay(Long userId, String tokenHash) {
        for (int attempt = 0; attempt < PROCESSING_RETRIES; attempt++) {
            AppointmentConfirmResponse response = replay(userId, tokenHash);
            if (response != null) {
                return response;
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw error("预约确认被中断，请重试", ResultCodeEnum.SERVICE_ERROR);
            }
        }
        throw error("预约正在处理中，请稍后重试", ResultCodeEnum.REPEAT_SUBMIT);
    }

    private static ViewAppointment toAppointment(AppointmentDraft draft) {
        ViewAppointment appointment = new ViewAppointment();
        appointment.setUserId(draft.userId());
        appointment.setRoomId(draft.roomId());
        appointment.setApartmentId(draft.apartmentId());
        appointment.setName(draft.name());
        appointment.setPhone(draft.phone());
        appointment.setAppointmentTime(Date.from(
                draft.appointmentTime().atZone(ZoneId.systemDefault()).toInstant()));
        appointment.setAdditionalInfo(draft.additionalInfo());
        appointment.setAppointmentStatus(AppointmentStatus.WAITING);
        return appointment;
    }

    private static String payload(ViewAppointment appointment) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("appointmentId", appointment.getId());
        payload.put("userId", appointment.getUserId());
        payload.put("roomId", appointment.getRoomId());
        payload.put("apartmentId", appointment.getApartmentId());
        payload.put("name", appointment.getName());
        payload.put("phone", appointment.getPhone());
        payload.put("appointmentTime", appointment.getAppointmentTime());
        payload.put("additionalInfo", appointment.getAdditionalInfo());
        payload.put("appointmentStatus", appointment.getAppointmentStatus().name());
        payload.put("messageType", "CREATE");
        return JsonUtil.toJsonString(payload);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void completeBestEffort(Long userId, String token) {
        try {
            draftStore.complete(userId, token);
        } catch (RuntimeException exception) {
            LOGGER.warn("Appointment committed but Redis draft cleanup failed for user {}", userId, exception);
        }
    }

    private void releaseBestEffort(Long userId, String token) {
        try {
            draftStore.release(userId, token);
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not release appointment draft claim for user {}", userId, exception);
        }
    }

    private static LeaseException error(String message, ResultCodeEnum code) {
        return new LeaseException(message, code.getCode());
    }
}
