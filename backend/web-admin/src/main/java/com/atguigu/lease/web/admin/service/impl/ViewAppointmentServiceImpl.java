package com.atguigu.lease.web.admin.service.impl;

import com.atguigu.lease.model.entity.ViewAppointment;
import com.atguigu.lease.model.enums.AppointmentStatus;
import com.atguigu.lease.service.MessageService;
import com.atguigu.lease.web.admin.mapper.ViewAppointmentMapper;
import com.atguigu.lease.web.admin.service.ViewAppointmentService;
import com.atguigu.lease.web.admin.vo.appointment.AppointmentQueryVo;
import com.atguigu.lease.web.admin.vo.appointment.AppointmentVo;
import com.atguigu.lease.message.appointment.AppointmentMessage;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author liubo
 * @description 针对表【view_appointment(预约看房信息表)】的数据库操作Service实现
 * @createDate 2023-07-24 15:48:00
 */
@Service
public class ViewAppointmentServiceImpl extends ServiceImpl<ViewAppointmentMapper, ViewAppointment>
        implements ViewAppointmentService {

    @Autowired
    private ViewAppointmentMapper viewAppointmentMapper;

    @Autowired
    private MessageService messageService;

    @Override
    public IPage<AppointmentVo> pageAppointmentByQuery(IPage<AppointmentVo> page, AppointmentQueryVo queryVo) {
        return viewAppointmentMapper.pageAppointmentByQuery(page, queryVo);
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
            // 创建消息对象
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
                    .createTime(new java.util.Date())
                    .build();

            try {
                // 发送创建消息（用于通知）
                messageService.sendAppointmentCreateMessage(message);
            } catch (Exception e) {
                // 如果消息发送失败，记录日志但不影响保存操作
                System.err.println("消息发送失败，但预约已保存: " + e.getMessage());
            }
        }

        return result;
    }

    @Override
    public boolean updateStatusWithMessage(Long id, String status, String operationType) {
        // 更新预约状态
        ViewAppointment appointment = this.getById(id);
        if (appointment == null) {
            return false;
        }

        appointment.setAppointmentStatus(AppointmentStatus.valueOf(status));
        boolean result = this.updateById(appointment);

        if (result) {
            // 发送状态变更通知
            String messageContent = getOperationMessage(operationType);
            messageService.sendAppointmentStatusChangeMessage(
                    appointment.getId(),
                    appointment.getUserId(),
                    appointment.getName(),
                    appointment.getPhone(),
                    appointment.getApartmentId(),
                    appointment.getAppointmentTime(),
                    appointment.getAdditionalInfo(),
                    status,
                    operationType,
                    messageContent
            );
        }

        return result;
    }

    private String getOperationMessage(String operationType) {
        switch (operationType) {
            case "CANCEL":
                return "您的预约已被取消";
            case "CONFIRM":
                return "您的预约已确认";
            case "VIEWED":
                return "您的预约已看房完成";
            default:
                return "预约状态已更新";
        }
    }
}




