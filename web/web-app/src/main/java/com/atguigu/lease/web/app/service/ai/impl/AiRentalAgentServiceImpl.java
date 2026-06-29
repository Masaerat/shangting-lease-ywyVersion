package com.atguigu.lease.web.app.service.ai.impl;

import com.atguigu.lease.web.app.service.ai.AiRentalAgentService;
import com.atguigu.lease.web.app.service.ai.RentalKnowledgeService;
import com.atguigu.lease.web.app.service.ai.RentalRoomToolService;
import com.atguigu.lease.web.app.service.ai.model.RentalIntent;
import com.atguigu.lease.web.app.service.ai.model.RentalKnowledgeChunk;
import com.atguigu.lease.web.app.service.ai.support.AiChatRequestValidator;
import com.atguigu.lease.web.app.service.ai.support.RentalIntentParser;
import com.atguigu.lease.web.app.vo.ai.AiChatRequestVo;
import com.atguigu.lease.web.app.vo.ai.AiChatResponseVo;
import com.atguigu.lease.web.app.vo.ai.AiCitationVo;
import com.atguigu.lease.web.app.vo.ai.AiPreferenceVo;
import com.atguigu.lease.web.app.vo.ai.AiRecommendedRoomVo;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AiRentalAgentServiceImpl implements AiRentalAgentService {

    private final AiChatRequestValidator requestValidator;
    private final RentalIntentParser intentParser;
    private final RentalKnowledgeService rentalKnowledgeService;
    private final RentalRoomToolService rentalRoomToolService;

    public AiRentalAgentServiceImpl(AiChatRequestValidator requestValidator,
                                    RentalIntentParser intentParser,
                                    RentalKnowledgeService rentalKnowledgeService,
                                    RentalRoomToolService rentalRoomToolService) {
        this.requestValidator = requestValidator;
        this.intentParser = intentParser;
        this.rentalKnowledgeService = rentalKnowledgeService;
        this.rentalRoomToolService = rentalRoomToolService;
    }

    @Override
    public AiChatResponseVo chat(AiChatRequestVo requestVo) {
        requestValidator.validate(requestVo);

        RentalIntent intent = intentParser.parse(requestVo.getMessage());
        AiPreferenceVo preferences = mergePreferences(requestVo.getPreferences(), intent);

        List<AiRecommendedRoomVo> rooms = intent.isRoomSearchRequested()
                ? rentalRoomToolService.searchRooms(preferences, 3)
                : List.of();
        List<RentalKnowledgeChunk> knowledgeChunks = intent.isRentalQuestionRequested()
                ? rentalKnowledgeService.search(requestVo.getMessage(), 3)
                : List.of();

        AiChatResponseVo responseVo = new AiChatResponseVo();
        responseVo.setSessionId(resolveSessionId(requestVo.getSessionId()));
        responseVo.setRecommendedRooms(rooms);
        responseVo.setCitations(toCitations(knowledgeChunks));
        responseVo.setSuggestedActions(buildSuggestedActions(rooms, knowledgeChunks));
        responseVo.setAnswer(buildAnswer(intent, rooms, knowledgeChunks));
        return responseVo;
    }

    private AiPreferenceVo mergePreferences(AiPreferenceVo requestPreferences, RentalIntent intent) {
        AiPreferenceVo preferences = requestPreferences == null ? new AiPreferenceVo() : requestPreferences;
        if (preferences.getMinRent() == null && intent.getMinRent() != null) {
            preferences.setMinRent(intent.getMinRent());
        }
        if (preferences.getMaxRent() == null && intent.getMaxRent() != null) {
            preferences.setMaxRent(intent.getMaxRent());
        }
        if (preferences.getOrderType() == null) {
            preferences.setOrderType("asc");
        }
        return preferences;
    }

    private String resolveSessionId(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return UUID.randomUUID().toString();
        }
        return sessionId;
    }

    private List<AiCitationVo> toCitations(List<RentalKnowledgeChunk> chunks) {
        return chunks.stream()
                .map(chunk -> new AiCitationVo(chunk.getTitle(), chunk.getCategory(), chunk.getSource(), chunk.getContent()))
                .toList();
    }

    private List<String> buildSuggestedActions(List<AiRecommendedRoomVo> rooms, List<RentalKnowledgeChunk> chunks) {
        List<String> actions = new ArrayList<>();
        if (!rooms.isEmpty()) {
            actions.add("查看推荐房源详情");
            actions.add("选择意向房源后提交预约看房");
        }
        if (!chunks.isEmpty()) {
            actions.add("阅读租房事项说明并向管家确认合同条款");
        }
        if (actions.isEmpty()) {
            actions.add("补充预算、区域、户型或租期要求");
        }
        return actions;
    }

    private String buildAnswer(RentalIntent intent, List<AiRecommendedRoomVo> rooms, List<RentalKnowledgeChunk> chunks) {
        StringBuilder answer = new StringBuilder();
        if (intent.isRoomSearchRequested()) {
            if (rooms.isEmpty()) {
                answer.append("暂时没有找到完全匹配的房源，可以放宽预算、区域或租期条件后再试。");
            } else {
                answer.append("我按你的描述筛选了可租房源，优先推荐租金更接近预算、信息更完整的房间。");
            }
        }
        if (!chunks.isEmpty()) {
            if (!answer.isEmpty()) {
                answer.append("\n\n");
            }
            answer.append("关于租房事项：");
            for (RentalKnowledgeChunk chunk : chunks) {
                answer.append("\n- ").append(chunk.getContent());
            }
        }
        if (answer.isEmpty()) {
            answer.append("你可以告诉我预算、区域、户型、通勤或租期要求，我会帮你筛选房源并说明相关租房事项。");
        }
        return answer.toString();
    }
}
