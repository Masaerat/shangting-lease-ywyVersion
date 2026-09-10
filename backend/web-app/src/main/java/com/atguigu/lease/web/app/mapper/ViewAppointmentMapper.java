package com.atguigu.lease.web.app.mapper;

import com.atguigu.lease.model.entity.ViewAppointment;
import com.atguigu.lease.web.app.vo.appointment.AppointmentItemVo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
* @author liubo
* @description 针对表【view_appointment(预约看房信息表)】的数据库操作Mapper
* @createDate 2023-07-26 11:12:39
* @Entity com.atguigu.lease.model.entity.ViewAppointment
*/
public interface ViewAppointmentMapper extends BaseMapper<ViewAppointment> {

    List<AppointmentItemVo> listItemByUserId(Long userId);

    @Select("""
            SELECT COUNT(*) FROM view_appointment
            WHERE user_id = #{userId} AND room_id = #{roomId}
              AND appointment_status = 1 AND is_deleted = 0
            """)
    long countActiveByUserAndRoom(Long userId, Long roomId);
}




