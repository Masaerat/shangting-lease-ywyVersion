package com.atguigu.lease.web.app.tools;

import com.atguigu.lease.web.app.service.ai.rag.KnowledgeSearchResult;
import com.atguigu.lease.web.app.service.ai.rag.RentalKnowledgeService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class RentalKnowledgeTool {

    private final RentalKnowledgeService knowledgeService;

    public RentalKnowledgeTool(RentalKnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @Tool(name = "search_rental_knowledge",
            description = "Search cited rental policy knowledge such as deposit, payment, appointment, repair and checkout rules.")
    public KnowledgeSearchResult search(
            @ToolParam(description = "The user's rental policy question") String question,
            @ToolParam(required = false, description = "Optional category such as DEPOSIT, PAYMENT, APPOINTMENT, REPAIR or CHECKOUT") String category,
            @ToolParam(required = false, description = "Maximum citations, 1 to 5") Integer limit,
            ToolContext context) {
        KnowledgeSearchResult result = knowledgeService.search(question, category, limit == null ? 5 : limit);
        AgentToolSupport.state(context).recordObservation("search_rental_knowledge", result);
        return result;
    }
}
