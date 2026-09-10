package com.atguigu.lease.web.admin.controller.lease;


import com.atguigu.lease.common.result.Result;
import com.atguigu.lease.common.result.ResultCodeEnum;
import com.atguigu.lease.model.entity.ViewAppointment;
import com.atguigu.lease.model.enums.AppointmentStatus;
import com.atguigu.lease.web.admin.service.ViewAppointmentService;
import com.atguigu.lease.web.admin.vo.appointment.AppointmentQueryVo;
import com.atguigu.lease.web.admin.vo.appointment.AppointmentVo;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "预约看房管理")
@RequestMapping("/admin/appointment")
@RestController
public class ViewAppointmentController {

    @Autowired
    private ViewAppointmentService viewAppointmentService;

    @Operation(summary = "分页查询预约信息")
    @GetMapping("page")
    public Result<IPage<AppointmentVo>> page(@RequestParam long current, @RequestParam long size, AppointmentQueryVo queryVo) {
        //分页信息+其他查询条件信息
        IPage<AppointmentVo> page = new Page<>(current, size);//封装分页信息
        IPage<AppointmentVo> list = viewAppointmentService.pageAppointmentByQuery(page, queryVo);//传入查询信息
        return Result.ok(list);
    }

    @Operation(summary = "根据id更新预约状态")
    @PostMapping("updateStatusById")
    public Result updateStatusById(@RequestParam Long id, @RequestParam AppointmentStatus status, @RequestParam(defaultValue = "UPDATE") String operationType) {
        // 使用新方法更新状态并发送消息
        boolean success = viewAppointmentService.updateStatusWithMessage(id, status.name(), operationType);
        return success ? Result.ok() : Result.fail(ResultCodeEnum.APP_APPOINTMENT_UPDATE_ERROR_ERROR.getCode(), "更新失败");
    }

    @Operation(summary = "新增预约（带消息发送）")
    @PostMapping("save")
    public Result save(@RequestBody ViewAppointment viewAppointment) {
        // 使用新方法保存并发送消息
        boolean success = viewAppointmentService.saveWithMessage(viewAppointment);
        return success ? Result.ok() : Result.fail(ResultCodeEnum.APP_APPOINTMENT_SAVE_ERROR_ERROR.getCode(), "保存失败");
    }

}