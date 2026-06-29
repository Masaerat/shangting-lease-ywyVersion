package com.atguigu.lease.web.app.service.ai.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RentalKnowledgeChunk {

    private String title;

    private String category;

    private String source;

    private String content;
}
