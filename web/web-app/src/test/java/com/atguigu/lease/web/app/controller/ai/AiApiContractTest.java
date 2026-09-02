package com.atguigu.lease.web.app.controller.ai;

import com.atguigu.lease.common.utils.JwtUtil;
import com.atguigu.lease.common.exception.GlobalExceptionHandler;
import com.atguigu.lease.web.app.custom.interceptor.AuthenticationInterceptor;
import com.atguigu.lease.web.app.service.ai.RentalChatService;
import com.atguigu.lease.web.app.service.ai.appointment.AppointmentConfirmationService;
import com.atguigu.lease.web.app.service.ai.appointment.AppointmentDraftService;
import com.atguigu.lease.web.app.vo.ai.AiChatMetaVo;
import com.atguigu.lease.web.app.vo.ai.ChatSseEvent;
import com.atguigu.lease.web.app.vo.ai.appointment.AppointmentConfirmResponse;
import com.atguigu.lease.web.app.vo.ai.appointment.AppointmentDraftResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AiApiContractTest {

    private RentalChatService chatService;
    private AppointmentDraftService draftService;
    private AppointmentConfirmationService confirmationService;
    private MockMvc mockMvc;
    private String token;

    @BeforeEach
    void setUp() {
        chatService = mock(RentalChatService.class);
        draftService = mock(AppointmentDraftService.class);
        confirmationService = mock(AppointmentConfirmationService.class);

        AiChatController chatController = new AiChatController();
        ReflectionTestUtils.setField(chatController, "rentalChatService", chatService);
        AiAppointmentController appointmentController =
                new AiAppointmentController(draftService, confirmationService);
        mockMvc = standaloneSetup(chatController, appointmentController)
                .addInterceptors(new AuthenticationInterceptor())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        token = JwtUtil.createToken(7L, "13800000000");
    }

    @Test
    void chatReturnsStablePostSseEnvelope() throws Exception {
        doAnswer(invocation -> {
            SseEmitter emitter = invocation.getArgument(1);
            emitter.send(SseEmitter.event().name("chat").data(new ChatSseEvent(
                    "meta", new AiChatMetaVo("FALLBACK", "contract", "local-rules", "trace-1"))));
            emitter.send(SseEmitter.event().name("chat").data(new ChatSseEvent(
                    "done", Map.of("traceId", "trace-1", "suggestedAction", "SELECT_ROOM"))));
            emitter.complete();
            return null;
        }).when(chatService).chat(any(), any(SseEmitter.class));

        MvcResult pending = mockMvc.perform(post("/app/ai/chat")
                        .header("access-token", token)
                        .contentType("application/json")
                        .accept("text/event-stream")
                        .content("{\"conversationId\":\"contract\",\"message\":\"预算2500元\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("event:chat"),
                        org.hamcrest.Matchers.containsString("\"type\":\"meta\""),
                        org.hamcrest.Matchers.containsString("\"traceId\":\"trace-1\""),
                        org.hamcrest.Matchers.containsString("\"suggestedAction\":\"SELECT_ROOM\""))));
    }

    @Test
    void draftAndConfirmApisUseAuthenticatedUserAndKeepTwoStepsSeparate() throws Exception {
        when(draftService.create(eq(7L), any())).thenReturn(new AppointmentDraftResponse(
                "draft-token", Instant.parse("2026-09-02T13:10:00Z"),
                930001L, 920001L, "张三", "13800000000",
                LocalDateTime.parse("2026-09-03T10:00:00"), null));
        when(confirmationService.confirm(7L, "draft-token"))
                .thenReturn(new AppointmentConfirmResponse(101L, false));

        mockMvc.perform(post("/app/ai/appointments/draft")
                        .header("access-token", token)
                        .contentType("application/json")
                        .content("""
                                {"roomId":930001,"name":"张三","phone":"13800000000",
                                 "appointmentTime":"2026-09-03T10:00:00"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.confirmationToken").value("draft-token"))
                .andExpect(jsonPath("$.data.roomId").value(930001));
        verify(draftService).create(eq(7L), any());

        mockMvc.perform(post("/app/ai/appointments/confirm")
                        .header("access-token", token)
                        .contentType("application/json")
                        .content("{\"confirmationToken\":\"draft-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.appointmentId").value(101))
                .andExpect(jsonPath("$.data.idempotentReplay").value(false));
        verify(confirmationService).confirm(7L, "draft-token");
    }

    @Test
    void protectedAiApiRejectsMissingAccessTokenBeforeBusinessCall() throws Exception {
        mockMvc.perform(post("/app/ai/appointments/confirm")
                        .contentType("application/json")
                        .content("{\"confirmationToken\":\"draft-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(305))
                .andExpect(jsonPath("$.message").value("未登陆"));

        verifyNoInteractions(confirmationService);
    }
}
