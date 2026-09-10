package com.atguigu.lease.web.app.tools;

import com.atguigu.lease.model.entity.PaymentType;
import com.atguigu.lease.model.entity.RoomInfo;
import com.atguigu.lease.model.enums.ReleaseStatus;
import com.atguigu.lease.web.app.service.PaymentTypeService;
import com.atguigu.lease.web.app.service.RoomInfoService;
import com.atguigu.lease.web.app.service.ai.agent.AgentExecutionState;
import com.atguigu.lease.web.app.service.ai.appointment.AppointmentDraftService;
import com.atguigu.lease.web.app.vo.ai.appointment.AppointmentDraftResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ToolContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentBusinessToolsTest {

    @Test
    void calculatesMoveInCostFromCurrentRentAndPaymentOption() {
        RoomInfoService roomService = mock(RoomInfoService.class);
        PaymentTypeService paymentService = mock(PaymentTypeService.class);
        RoomInfo room = new RoomInfo();
        room.setId(930001L);
        room.setRent(new BigDecimal("2300"));
        room.setIsRelease(ReleaseStatus.RELEASED);
        PaymentType quarterly = new PaymentType();
        quarterly.setName("季付");
        quarterly.setPayMonthCount("3");
        quarterly.setAdditionalInfo("押一付三");
        when(roomService.getById(930001L)).thenReturn(room);
        when(paymentService.listByRoomId(930001L)).thenReturn(List.of(quarterly));

        MoveInCostTool.MoveInCostResult result = new MoveInCostTool(roomService, paymentService)
                .calculate(930001L, "季付", toolContext(7L));

        assertThat(result.rentPayment()).isEqualByComparingTo("6900");
        assertThat(result.deposit()).isEqualByComparingTo("2300");
        assertThat(result.estimatedFirstPayment()).isEqualByComparingTo("9200");
    }

    @Test
    void createsDraftForAuthenticatedToolContextUser() {
        AppointmentDraftService service = mock(AppointmentDraftService.class);
        AppointmentDraftResponse response = new AppointmentDraftResponse(
                "token", Instant.parse("2026-09-02T13:10:00Z"), 930001L, 920001L,
                "张三", "13800000000", LocalDateTime.parse("2026-09-03T10:00:00"), null);
        when(service.create(eq(7L), any())).thenReturn(response);
        AppointmentDraftTool tool = new AppointmentDraftTool(service);

        AppointmentDraftResponse actual = tool.create(
                930001L, "张三", "13800000000", "2026-09-03T10:00:00", null, toolContext(7L));

        assertThat(actual.confirmationToken()).isEqualTo("token");
        ArgumentCaptor<com.atguigu.lease.web.app.vo.ai.appointment.AppointmentDraftRequest> request =
                ArgumentCaptor.forClass(com.atguigu.lease.web.app.vo.ai.appointment.AppointmentDraftRequest.class);
        verify(service).create(eq(7L), request.capture());
        assertThat(request.getValue().getRoomId()).isEqualTo(930001L);
    }

    private ToolContext toolContext(Long userId) {
        return new ToolContext(Map.of(
                AgentExecutionState.CONTEXT_KEY, new AgentExecutionState(),
                AgentExecutionState.USER_ID_KEY, userId));
    }
}
