package com.atguigu.lease.web.app.controller.ai;

import com.atguigu.lease.common.result.Result;
import com.atguigu.lease.web.app.service.ai.rag.KnowledgeSearchResult;
import com.atguigu.lease.web.app.service.ai.rag.RentalKnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "APP-AI知识检索评测")
@RestController
@RequestMapping("/app/ai/rag")
public class AiRagController {

    private final RentalKnowledgeService knowledgeService;

    public AiRagController(RentalKnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @Operation(summary = "直接查看查询改写、检索模式、排序分数与知识引用")
    @GetMapping("/search")
    public Result<KnowledgeSearchResult> search(
            @RequestParam String question,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "5") int limit) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Question is required");
        }
        if (question.length() > 1000) {
            throw new IllegalArgumentException("Question is too long");
        }
        return Result.ok(knowledgeService.search(question, category, limit));
    }
}
