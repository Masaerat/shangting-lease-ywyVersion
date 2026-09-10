package com.atguigu.lease.web.app.tools;

import com.atguigu.lease.web.app.service.ai.appointment.AppointmentDraftService;
import com.atguigu.lease.web.app.vo.ai.appointment.AppointmentDraftRequest;
import com.atguigu.lease.web.app.vo.ai.appointment.AppointmentDraftResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class AppointmentDraftTool {

    private final AppointmentDraftService draftService;

    public AppointmentDraftTool(AppointmentDraftService draftService) {
        this.draftService = draftService;
    }

    @Tool(name = "create_appointment_draft",
            description = "Prepare a temporary appointment draft after the user supplied a room, future time, name and phone. This never confirms or creates a database appointment.")
    public AppointmentDraftResponse create(
            @ToolParam(description = "Room primary key") Long roomId,
            @ToolParam(description = "Contact name") String name,
            @ToolParam(description = "Chinese mobile phone") String phone,
            @ToolParam(description = "Future local date time in ISO-8601 format") String appointmentTime,
            @ToolParam(required = false, description = "Optional note") String additionalInfo,
            ToolContext context) {
        AppointmentDraftRequest request = new AppointmentDraftRequest();
        request.setRoomId(roomId);
        request.setName(name);
        request.setPhone(phone);
        request.setAppointmentTime(LocalDateTime.parse(appointmentTime));
        request.setAdditionalInfo(additionalInfo);
        AppointmentDraftResponse result = draftService.create(AgentToolSupport.userId(context), request);
        AgentToolSupport.state(context).recordObservation("create_appointment_draft", result);
        return result;
    }
}
