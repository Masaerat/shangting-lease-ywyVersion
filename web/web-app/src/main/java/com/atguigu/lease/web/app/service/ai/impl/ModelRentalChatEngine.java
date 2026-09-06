package com.atguigu.lease.web.app.service.ai.impl;

import com.atguigu.lease.config.ai.AiAgentProperties;
import com.atguigu.lease.web.app.service.ai.RentalChatEngine;
import com.atguigu.lease.web.app.service.ai.agent.AgentContext;
import com.atguigu.lease.web.app.service.ai.agent.AgentGoal;
import com.atguigu.lease.web.app.service.ai.agent.AgentObservation;
import com.atguigu.lease.web.app.service.ai.agent.AgentResult;
import com.atguigu.lease.web.app.service.ai.agent.RentalAgentRuntime;
import com.atguigu.lease.web.app.service.ai.rag.KnowledgeSearchResult;
import com.atguigu.lease.web.app.tools.RoomSearchTool;
import com.atguigu.lease.web.app.vo.ai.AiChatMetaVo;
import com.atguigu.lease.web.app.vo.ai.AiRecommendationVo;
import com.atguigu.lease.web.app.vo.ai.ChatSseEvent;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service("modelRentalChatEngine")
public class ModelRentalChatEngine implements RentalChatEngine {

    private final ObjectProvider<RentalAgentRuntime> runtimeProvider;
    private final AiAgentProperties properties;

    public ModelRentalChatEngine(ObjectProvider<RentalAgentRuntime> runtimeProvider,
                                 AiAgentProperties properties) {
        this.runtimeProvider = runtimeProvider;
        this.properties = properties;
    }

    @Override
    public String mode() {
        return "MODEL";
    }

    @Override
    public boolean available() {
        RentalAgentRuntime runtime = runtimeProvider.getIfAvailable();
        return runtime != null && runtime.available();
    }

    @Override
    public void chat(ChatExecution execution, Consumer<ChatSseEvent> sink) {
        RentalAgentRuntime runtime = runtimeProvider.getIfAvailable();
        if (runtime == null || !runtime.available()) {
            throw new IllegalStateException("Rental agent runtime is unavailable");
        }
        AgentContext context = new AgentContext(
                execution.userId(), execution.conversationId(), execution.message(), execution.history(),
                detectGoal(execution.message()), properties.getMaxSteps());
        AgentResult result = runtime.execute(context);

        sink.accept(new ChatSseEvent("meta", new AiChatMetaVo(
                mode(), execution.conversationId(), result.model(), result.traceId())));
        sink.accept(new ChatSseEvent("message", result.answer()));
        sink.accept(new ChatSseEvent("recommendations", recommendations(result.observations())));
        sink.accept(new ChatSseEvent("citations", citations(result.observations())));
        for (AgentObservation observation : result.observations()) {
            String eventType = switch (observation.tool()) {
                case "create_appointment_draft" -> "appointment_draft";
                case "get_appointment_status" -> "appointment_status";
                case "list_my_notifications" -> "notifications";
                default -> null;
            };
            if (eventType != null) sink.accept(new ChatSseEvent(eventType, observation.payload()));
        }
        if (!result.trajectory().isEmpty()) {
            sink.accept(new ChatSseEvent("trajectory", result.trajectory()));
        }
        sink.accept(new ChatSseEvent("done", Map.of(
                "traceId", result.traceId(),
                "suggestedAction", suggestedAction(result.observations()))));
    }

    AgentGoal detectGoal(String message) {
        String value = message == null ? "" : message;
        boolean room = containsAny(value, "预算", "房源", "房间", "租金", "月租", "公寓", "区", "市");
        boolean policy = containsAny(value, "押金", "付款", "月付", "季付", "预约", "看房",
                "报修", "维修", "退租", "结算", "违约", "入住材料");
        boolean appointment = containsAny(value, "帮我预约", "预约这个", "预约看房");
        if (appointment) return AgentGoal.PREPARE_APPOINTMENT;
        if (room && policy) return AgentGoal.FIND_ROOM_AND_ANSWER_POLICY;
        if (room) return AgentGoal.FIND_ROOM;
        if (policy) return AgentGoal.ANSWER_POLICY;
        return AgentGoal.ANSWER;
    }

    private List<AiRecommendationVo> recommendations(List<AgentObservation> observations) {
        List<AiRecommendationVo> result = new ArrayList<>();
        for (AgentObservation observation : observations) {
            if (!"search_available_rooms".equals(observation.tool())
                    || !(observation.payload() instanceof List<?> rooms)) {
                continue;
            }
            rooms.stream().filter(RoomSearchTool.RoomHit.class::isInstance)
                    .map(RoomSearchTool.RoomHit.class::cast)
                    .map(room -> new AiRecommendationVo(
                            room.roomId(), room.apartmentId(), room.apartment(), room.roomNumber(), room.rent()))
                    .forEach(result::add);
        }
        return List.copyOf(result);
    }

    private List<?> citations(List<AgentObservation> observations) {
        return observations.stream()
                .filter(item -> "search_rental_knowledge".equals(item.tool()))
                .map(AgentObservation::payload)
                .filter(KnowledgeSearchResult.class::isInstance)
                .map(KnowledgeSearchResult.class::cast)
                .flatMap(result -> result.citations().stream())
                .toList();
    }

    private String suggestedAction(List<AgentObservation> observations) {
        boolean drafted = observations.stream().anyMatch(item -> "create_appointment_draft".equals(item.tool()));
        if (drafted) return "CONFIRM_APPOINTMENT";
        boolean rooms = observations.stream().anyMatch(item -> "search_available_rooms".equals(item.tool()));
        return rooms ? "SELECT_ROOM" : "NONE";
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if (value.contains(term)) return true;
        }
        return false;
    }
}
