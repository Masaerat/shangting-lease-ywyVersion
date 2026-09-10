package com.atguigu.lease.web.app.service.ai.impl;

import com.atguigu.lease.web.app.service.ai.RentalChatEngine;
import com.atguigu.lease.notification.AppointmentNotificationStore;
import com.atguigu.lease.notification.AppointmentDeliveryStatus;
import com.atguigu.lease.web.app.service.ai.rag.KnowledgeCitation;
import com.atguigu.lease.web.app.service.ai.rag.KnowledgeSearchResult;
import com.atguigu.lease.web.app.service.ai.rag.RentalKnowledgeService;
import com.atguigu.lease.web.app.tools.RoomSearchTool;
import com.atguigu.lease.web.app.vo.ai.AiChatMetaVo;
import com.atguigu.lease.web.app.vo.ai.AiRecommendationVo;
import com.atguigu.lease.web.app.vo.ai.ChatSseEvent;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service("fallbackRentalChatEngine")
public class FallbackRentalChatEngine implements RentalChatEngine {

    private static final Pattern RANGE = Pattern.compile("(\\d{3,6})\\s*(?:-|到|至)\\s*(\\d{3,6})");
    private static final Pattern MAX_RENT = Pattern.compile("(?:预算|不超过|以内|最高|最多|月租)[^\\d]{0,6}(\\d{3,6})");
    private static final Pattern MIN_RENT = Pattern.compile("(?:至少|最低|起租)[^\\d]{0,6}(\\d{3,6})");
    private static final Pattern CITY = Pattern.compile("([\\p{IsHan}]{2,6}市)");
    private static final Pattern DISTRICT = Pattern.compile("([\\p{IsHan}]{2,6}(?:区|县))");

    private final RoomSearchTool roomSearchTool;
    private final RentalKnowledgeService knowledgeService;
    private final AppointmentNotificationStore notifications;

    public FallbackRentalChatEngine(RoomSearchTool roomSearchTool, RentalKnowledgeService knowledgeService,
                                    AppointmentNotificationStore notifications) {
        this.roomSearchTool = roomSearchTool;
        this.knowledgeService = knowledgeService;
        this.notifications = notifications;
    }

    @Override
    public String mode() {
        return "FALLBACK";
    }

    @Override
    public void chat(ChatExecution execution, Consumer<ChatSseEvent> sink) {
        if (queryDelivery(execution, sink)) return;
        RentRange range = rentRange(execution.message());
        String city = group(CITY, execution.message());
        String district = group(DISTRICT, execution.message());
        List<RoomSearchTool.RoomHit> rooms = roomSearchTool.searchRooms(
                city, district, range.minRent(), range.maxRent());
        KnowledgeSearchResult knowledge = knowledgeService.search(execution.message(), null, 5);
        List<AiRecommendationVo> recommendations = rooms.stream()
                .map(room -> new AiRecommendationVo(
                        room.roomId(), room.apartmentId(), room.apartment(), room.roomNumber(), room.rent()))
                .toList();
        String traceId = UUID.randomUUID().toString();

        sink.accept(new ChatSseEvent("meta", new AiChatMetaVo(
                mode(), execution.conversationId(), "local-rules", traceId)));
        sink.accept(new ChatSseEvent("message", answer(rooms, knowledge)));
        sink.accept(new ChatSseEvent("recommendations", recommendations));
        sink.accept(new ChatSseEvent("citations", knowledge.citations()));
        sink.accept(new ChatSseEvent("done", Map.of(
                "traceId", traceId,
                "suggestedAction", rooms.isEmpty() ? "NONE" : "SELECT_ROOM")));
    }

    private boolean queryDelivery(ChatExecution execution, Consumer<ChatSseEvent> sink) {
        String message = execution.message();
        boolean statusQuery = message.contains("预约") &&
                (message.contains("状态") || message.contains("成功") || message.contains("进度") || message.contains("送达"));
        boolean notificationQuery = message.contains("通知") || message.contains("消息列表");
        if (!statusQuery && !notificationQuery) return false;
        String traceId = UUID.randomUUID().toString();
        String answer;
        Object result = null;
        String type;
        if (statusQuery) {
            type = "appointment_status";
            Matcher matcher = Pattern.compile("(?:预约(?:编号|ID|id|号)?[：:#\\s]*)([0-9]{1,18})(?![0-9])").matcher(message);
            if (!matcher.find()) {
                answer = "请提供确认接口返回的预约编号，例如：预约123的状态。草稿 token 和房间编号不是预约编号。";
            } else {
                AppointmentDeliveryStatus status = notifications.status(execution.userId(), Long.valueOf(matcher.group(1)));
                result = status;
                answer = status == null ? "未找到该预约。" : "预约编号：" + status.appointmentId()
                        + "；预约状态：" + switch (status.appointmentStatus() == null ? 0 : status.appointmentStatus()) {
                            case 1 -> "待看房";
                            case 2 -> "已取消";
                            case 3 -> "已看房";
                            default -> "未知";
                        } + "；站内通知：" + switch (status.deliveryStatus()) {
                            case "DELIVERED" -> "已送达站内通知，不代表短信已发送。";
                            case "PUBLISHED" -> "MQ 已接收，尚未查到通知落库；不是送达成功。";
                            case "PENDING" -> "等待异步投递。";
                            case "FAILED" -> "发布重试耗尽，需运维核查；预约不会因此被取消。";
                            default -> "暂无可确认的投递记录。";
                        };
            }
        } else {
            type = "notifications";
            var items = notifications.list(execution.userId(), 20);
            result = items;
            answer = items.isEmpty() ? "暂无已送达的站内通知，这不代表您没有预约。"
                    : "查到 " + items.size() + " 条站内通知，详情见通知列表。";
        }
        sink.accept(new ChatSseEvent("meta", new AiChatMetaVo(mode(), execution.conversationId(), "local-rules", traceId)));
        sink.accept(new ChatSseEvent("message", answer));
        sink.accept(new ChatSseEvent(type, result));
        sink.accept(new ChatSseEvent("done", Map.of("traceId", traceId, "suggestedAction", "NONE")));
        return true;
    }

    private String answer(List<RoomSearchTool.RoomHit> rooms,
                          KnowledgeSearchResult knowledge) {
        String roomSummary = rooms.isEmpty()
                ? "暂未找到完全符合条件的在租房源，可以适当放宽预算或区域。"
                : "找到 " + rooms.size() + " 套符合条件的在租房源，已整理在推荐列表中。";
        String knowledgeSummary = knowledge.citations().isEmpty()
                ? " 暂无相关政策证据，不能据此承诺具体租赁规则。"
                : summary(knowledge.citations().getFirst());
        return roomSummary + knowledgeSummary;
    }

    private String summary(KnowledgeCitation citation) {
        return " 关于" + citation.chapter() + "：" + citation.excerpt();
    }

    private RentRange rentRange(String message) {
        Matcher range = RANGE.matcher(message);
        if (range.find()) {
            return new RentRange(new BigDecimal(range.group(1)), new BigDecimal(range.group(2)));
        }
        return new RentRange(amount(MIN_RENT, message), amount(MAX_RENT, message));
    }

    private BigDecimal amount(Pattern pattern, String message) {
        Matcher matcher = pattern.matcher(message);
        return matcher.find() ? new BigDecimal(matcher.group(1)) : null;
    }

    private String group(Pattern pattern, String message) {
        Matcher matcher = pattern.matcher(message);
        return matcher.find() ? matcher.group(1) : null;
    }

    private record RentRange(BigDecimal minRent, BigDecimal maxRent) {
    }
}
