package com.atguigu.lease.message.appointment;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentMessage {

    /**
     * 预约ID
     */
    private Long appointmentId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户姓名
     */
    private String name;

    /**
     * 用户手机号
     */
    private String phone;

    /**
     * 公寓ID
     */
    private Long apartmentId;

    /**
     * 预约时间
     */
    private Date appointmentTime;

    /**
     * 备注信息
     */
    private String additionalInfo;

    /**
     * 预约状态
     */
    private String appointmentStatus;

    /**
     * 消息类型
     */
    private String messageType;

    /**
     * 创建时间
     */
    private Date createTime;
}