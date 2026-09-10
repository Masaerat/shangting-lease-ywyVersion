package com.atguigu.lease.web.app.service.impl;

import com.atguigu.lease.model.entity.ViewAppointment;
import com.atguigu.lease.model.entity.AppointmentEventOutbox;
import com.atguigu.lease.model.enums.AppointmentStatus;
import com.atguigu.lease.common.utils.JsonUtil;
import com.atguigu.lease.web.app.mapper.AppointmentEventOutboxMapper;
import com.atguigu.lease.web.app.mapper.ViewAppointmentMapper;
import com.atguigu.lease.web.app.service.ApartmentInfoService;
import com.atguigu.lease.web.app.service.ViewAppointmentService;
import com.atguigu.lease.web.app.vo.apartment.ApartmentItemVo;
import com.atguigu.lease.web.app.vo.appointment.AppointmentDetailVo;
import com.atguigu.lease.web.app.vo.appointment.AppointmentItemVo;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author liubo
 * @description 针对表【view_appointment(预约看房信息表)】的数据库操作Service实现
 * @createDate 2023-07-26 11:12:39
 */
@Service
public class ViewAppointmentServiceImpl extends ServiceImpl<ViewAppointmentMapper, ViewAppointment>
        implements ViewAppointmentService {

    private final ViewAppointmentMapper viewAppointmentMapper;
    private final ApartmentInfoService apartmentInfoService;
    private final AppointmentEventOutboxMapper outboxMapper;
    private final Clock clock;

    @Autowired
    public ViewAppointmentServiceImpl(ViewAppointmentMapper viewAppointmentMapper,
                                      ApartmentInfoService apartmentInfoService,
                                      AppointmentEventOutboxMapper outboxMapper) {
        this(viewAppointmentMapper, apartmentInfoService, outboxMapper, Clock.systemDefaultZone());
    }

    ViewAppointmentServiceImpl(ViewAppointmentMapper viewAppointmentMapper,
                               ApartmentInfoService apartmentInfoService,
                               AppointmentEventOutboxMapper outboxMapper,
                               Clock clock) {
        this.viewAppointmentMapper = viewAppointmentMapper;
        this.apartmentInfoService = apartmentInfoService;
        this.outboxMapper = outboxMapper;
        this.clock = clock;
    }

    @Override
    public List<AppointmentItemVo> listItemByUserId(Long userId) {
        return viewAppointmentMapper.listItemByUserId(userId);
    }

    @Override
    public AppointmentDetailVo getDetailById(Long id) {
        //根据id查询预约信息
        ViewAppointment viewAppointment = viewAppointmentMapper.selectById(id);
        //根据公寓id查询公寓信息apartmentItemVo
        ApartmentItemVo apartmentItemVo = apartmentInfoService.selectApartmentItemVoById(viewAppointment.getApartmentId());

        //拼接agreementDetailVo
        AppointmentDetailVo agreementDetailVo = new AppointmentDetailVo();
        BeanUtils.copyProperties(viewAppointment, agreementDetailVo);
        agreementDetailVo.setApartmentItemVo(apartmentItemVo);

        //返回agreementDetailVo
        return agreementDetailVo;
    }

    @Override
    @Transactional
    public boolean saveWithMessage(ViewAppointment entity) {
        if (entity.getAppointmentStatus() == null) {
            entity.setAppointmentStatus(AppointmentStatus.WAITING);
        }
        if (viewAppointmentMapper.insert(entity) != 1 || entity.getId() == null) {
            return false;
        }

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
        AppointmentEventOutbox outbox = new AppointmentEventOutbox();
        outbox.setAggregateId(entity.getId());
        outbox.setEventType("APPOINTMENT_CREATED");
        outbox.setPayloadJson(payload(entity));
        outbox.setStatus("PENDING");
        outbox.setAttempts(0);
        outbox.setNextAttemptAt(now);
        outbox.setCreatedAt(now);
        outboxMapper.insert(outbox);
        return true;
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
}
