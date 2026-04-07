package com.atguigu.lease.web.app.service.impl;

import com.atguigu.lease.model.entity.ViewAppointment;
import com.atguigu.lease.model.enums.AppointmentStatus;
import com.atguigu.lease.service.MessageService;
import com.atguigu.lease.web.app.mapper.ViewAppointmentMapper;
import com.atguigu.lease.web.app.service.ApartmentInfoService;
import com.atguigu.lease.web.app.service.ViewAppointmentService;
import com.atguigu.lease.web.app.vo.apartment.ApartmentItemVo;
import com.atguigu.lease.web.app.vo.appointment.AppointmentDetailVo;
import com.atguigu.lease.web.app.vo.appointment.AppointmentItemVo;
import com.atguigu.lease.message.appointment.AppointmentMessage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * @author liubo
 * @description 针对表【view_appointment(预约看房信息表)】的数据库操作Service实现
 * @createDate 2023-07-26 11:12:39
 */
@Service
public class ViewAppointmentServiceImpl extends ServiceImpl<ViewAppointmentMapper, ViewAppointment>
        implements ViewAppointmentService {

    @Autowired
    private ViewAppointmentMapper viewAppointmentMapper;

    @Autowired
    private ApartmentInfoService apartmentInfoService;

    @Autowired
    private MessageService messageService;


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
    public boolean saveWithMessage(ViewAppointment entity) {
        // 确保预约状态设置正确
        if (entity.getAppointmentStatus() == null) {
            entity.setAppointmentStatus(AppointmentStatus.WAITING);
        }

        // 保存到数据库
        boolean result = this.save(entity);

        if (result) {
            // 发送预约创建消息（消费者会处理短信发送）
            AppointmentMessage message = AppointmentMessage.builder()
                    .appointmentId(entity.getId())
                    .userId(entity.getUserId())
                    .name(entity.getName())
                    .phone(entity.getPhone())
                    .apartmentId(entity.getApartmentId())
                    .appointmentTime(entity.getAppointmentTime())
                    .additionalInfo(entity.getAdditionalInfo())
                    .appointmentStatus(entity.getAppointmentStatus().name())
                    .messageType("CREATE")
                    .createTime(new Date())
                    .build();

            // 发送消息到队列（立即处理）
            messageService.sendAppointmentCreateMessage(message);

            // 发送延迟消息（24小时后自动取消）
            messageService.sendDelayedCancelMessage(message);
        }

        return result;
    }
}