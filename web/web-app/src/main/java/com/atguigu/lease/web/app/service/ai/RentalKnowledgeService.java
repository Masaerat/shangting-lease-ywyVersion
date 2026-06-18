package com.atguigu.lease.web.app.service.ai;

import com.atguigu.lease.web.app.service.ai.model.RentalKnowledgeChunk;

import java.util.List;

public interface RentalKnowledgeService {

    List<RentalKnowledgeChunk> search(String query, int limit);
}
