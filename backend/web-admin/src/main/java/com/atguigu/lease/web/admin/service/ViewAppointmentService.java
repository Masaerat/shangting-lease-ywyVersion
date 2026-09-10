package com.atguigu.lease.web.admin.service;

import com.atguigu.lease.model.entity.ViewAppointment;
import com.atguigu.lease.web.admin.vo.appointment.AppointmentQueryVo;
import com.atguigu.lease.web.admin.vo.appointment.AppointmentVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author liubo
* @description 针对表【view_appointment(预约看房信息表)】的数据库操作Service
* @createDate 2023-07-24 15:48:00
*/
public interface ViewAppointmentService extends IService<ViewAppointment> {

    IPage<AppointmentVo> pageAppointmentByQuery(IPage<AppointmentVo> page, AppointmentQueryVo queryVo);

    /**
     * 保存预约并发送消息
     */
    boolean saveWithMessage(ViewAppointment entity);

    /**
     * 更新预约状态并发送通知
     */
    boolean updateStatusWithMessage(Long id, String status, String operationType);
}
