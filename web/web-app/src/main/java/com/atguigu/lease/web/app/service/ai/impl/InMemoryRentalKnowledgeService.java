package com.atguigu.lease.web.app.service.ai.impl;

import com.atguigu.lease.web.app.service.ai.RentalKnowledgeService;
import com.atguigu.lease.web.app.service.ai.model.RentalKnowledgeChunk;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class InMemoryRentalKnowledgeService implements RentalKnowledgeService {

    private final List<RentalKnowledgeChunk> chunks = List.of(
            new RentalKnowledgeChunk("押金与退还", "费用规则", "docs/ai-rental-agent/rag-knowledge.md",
                    "押金用于覆盖租期内可能产生的房屋损坏、欠费或违约费用。退租时应先完成房屋验收、水电物业结清，再按合同约定退还押金。"),
            new RentalKnowledgeChunk("预约看房流程", "看房预约", "docs/ai-rental-agent/rag-knowledge.md",
                    "租客可以先选择意向房源，再提交预约看房时间。平台不会由AI直接替用户提交预约，AI只生成建议和草稿。"),
            new RentalKnowledgeChunk("租金与付款方式", "费用规则", "docs/ai-rental-agent/rag-knowledge.md",
                    "房源页面展示月租金，具体付款方式以房源支持的付款类型和合同约定为准，常见方式包括月付、季付和押一付三。"),
            new RentalKnowledgeChunk("维修与入住", "入住服务", "docs/ai-rental-agent/rag-knowledge.md",
                    "入住前建议核对门锁、家电、水电表、家具和网络状态。租期内设施故障应及时联系公寓管理员或客服登记维修。")
    );

    @Override
    public List<RentalKnowledgeChunk> search(String query, int limit) {
        String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT);
        int safeLimit = Math.max(1, Math.min(limit, 5));

        List<RentalKnowledgeChunk> sorted = new ArrayList<>(chunks);
        sorted.sort(Comparator.comparingInt(chunk -> -score(normalized, chunk)));
        return sorted.stream()
                .filter(chunk -> score(normalized, chunk) > 0 || normalized.isBlank())
                .limit(safeLimit)
                .toList();
    }

    private int score(String query, RentalKnowledgeChunk chunk) {
        String haystack = (chunk.getTitle() + " " + chunk.getCategory() + " " + chunk.getContent()).toLowerCase(Locale.ROOT);
        int score = 0;
        for (String token : query.split("\\s+|，|。|,|\\?|？")) {
            if (!token.isBlank() && haystack.contains(token)) {
                score += 2;
            }
        }
        if (query.contains("押金") && haystack.contains("押金")) {
            score += 5;
        }
        if (query.contains("预约") && haystack.contains("预约")) {
            score += 5;
        }
        if (query.contains("付款") || query.contains("租金")) {
            if (haystack.contains("付款") || haystack.contains("租金")) {
                score += 5;
            }
        }
        return score;
    }
}
