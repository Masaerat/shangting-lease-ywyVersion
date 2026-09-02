package com.atguigu.lease.web.app.service.ai.rag;

public interface RentalKnowledgeService {

    KnowledgeSearchResult search(String question, String category, int limit);
}
