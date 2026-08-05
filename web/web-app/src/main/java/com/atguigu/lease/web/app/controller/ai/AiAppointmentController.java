package com.atguigu.lease.web.app.controller.ai;

import com.atguigu.lease.common.login.LoginUserHolder;
import com.atguigu.lease.common.result.Result;
import com.atguigu.lease.web.app.service.ai.appointment.AppointmentConfirmationService;
import com.atguigu.lease.web.app.service.ai.appointment.AppointmentDraftService;
import com.atguigu.lease.web.app.vo.ai.appointment.AppointmentConfirmRequest;
import com.atguigu.lease.web.app.vo.ai.appointment.AppointmentConfirmResponse;
import com.atguigu.lease.web.app.vo.ai.appointment.AppointmentDraftRequest;
import com.atguigu.lease.web.app.vo.ai.appointment.AppointmentDraftResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "APP-AI预约")
@RestController
@RequestMapping("/app/ai/appointments")
public class AiAppointmentController {

    private final AppointmentDraftService draftService;
    private final AppointmentConfirmationService confirmationService;

    public AiAppointmentController(AppointmentDraftService draftService,
                                   AppointmentConfirmationService confirmationService) {
        this.draftService = draftService;
        this.confirmationService = confirmationService;
    }

    @Operation(summary = "创建待确认的看房预约草稿")
    @PostMapping("/draft")
    public Result<AppointmentDraftResponse> createDraft(@RequestBody AppointmentDraftRequest request) {
        Long userId = LoginUserHolder.getLoginUser().getUserId();
        return Result.ok(draftService.create(userId, request));
    }

    @Operation(summary = "显式确认看房预约")
    @PostMapping("/confirm")
    public Result<AppointmentConfirmResponse> confirm(@RequestBody AppointmentConfirmRequest request) {
        Long userId = LoginUserHolder.getLoginUser().getUserId();
        return Result.ok(confirmationService.confirm(userId, request.getConfirmationToken()));
    }
}
