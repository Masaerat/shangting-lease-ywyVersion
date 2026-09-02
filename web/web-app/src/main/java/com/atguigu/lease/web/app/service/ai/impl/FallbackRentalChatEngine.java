package com.atguigu.lease.web.app.service.ai.impl;

import com.atguigu.lease.web.app.service.ai.RentalChatEngine;
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

    public FallbackRentalChatEngine(RoomSearchTool roomSearchTool, RentalKnowledgeService knowledgeService) {
        this.roomSearchTool = roomSearchTool;
        this.knowledgeService = knowledgeService;
    }

    @Override
    public String mode() {
        return "FALLBACK";
    }

    @Override
    public void chat(ChatExecution execution, Consumer<ChatSseEvent> sink) {
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

        sink.accept(new ChatSseEvent("meta", new AiChatMetaVo(mode(), execution.conversationId())));
        sink.accept(new ChatSseEvent("message", answer(rooms, knowledge)));
        sink.accept(new ChatSseEvent("recommendations", recommendations));
        sink.accept(new ChatSseEvent("citations", knowledge.citations()));
        sink.accept(new ChatSseEvent("done", null));
    }

    private String answer(List<RoomSearchTool.RoomHit> rooms,
                          KnowledgeSearchResult knowledge) {
        String roomSummary = rooms.isEmpty()
                ? "暂未找到完全符合条件的在租房源，可以适当放宽预算或区域。"
                : "找到 " + rooms.size() + " 套符合条件的在租房源，已整理在推荐列表中。";
        String knowledgeSummary = knowledge.citations().isEmpty()
                ? ""
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
