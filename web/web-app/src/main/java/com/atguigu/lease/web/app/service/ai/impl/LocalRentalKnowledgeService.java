package com.atguigu.lease.web.app.service.ai.impl;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class LocalRentalKnowledgeService {

    private final List<KnowledgeSection> sections;

    public LocalRentalKnowledgeService() {
        this.sections = loadSections();
    }

    public List<KnowledgeSection> search(String message) {
        String query = message == null ? "" : message.toLowerCase(Locale.ROOT);
        List<KnowledgeSection> matches = sections.stream()
                .filter(section -> matches(query, section.title()))
                .toList();
        return matches.isEmpty() ? sections.stream().limit(2).toList() : matches;
    }

    private boolean matches(String query, String title) {
        if (title.contains("押金") || title.contains("付款")) {
            return query.contains("押金") || query.contains("付款") || query.contains("月付") || query.contains("季付");
        }
        if (title.contains("预约")) {
            return query.contains("预约") || query.contains("看房");
        }
        if (title.contains("报修")) {
            return query.contains("报修") || query.contains("维修");
        }
        if (title.contains("退租")) {
            return query.contains("退租") || query.contains("结算") || query.contains("违约");
        }
        return false;
    }

    private List<KnowledgeSection> loadSections() {
        try {
            String markdown = new ClassPathResource("ai/rag-knowledge.md")
                    .getContentAsString(StandardCharsets.UTF_8);
            List<KnowledgeSection> result = new ArrayList<>();
            String title = null;
            StringBuilder content = new StringBuilder();
            for (String line : markdown.split("\\R")) {
                if (line.startsWith("# ")) {
                    addSection(result, title, content);
                    title = line.substring(2).trim();
                    content.setLength(0);
                } else if (!line.isBlank()) {
                    if (!content.isEmpty()) {
                        content.append(' ');
                    }
                    content.append(line.trim());
                }
            }
            addSection(result, title, content);
            return List.copyOf(result);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load local rental knowledge", e);
        }
    }

    private void addSection(List<KnowledgeSection> result, String title, StringBuilder content) {
        if (title != null && !content.isEmpty()) {
            result.add(new KnowledgeSection(title, content.toString(), "rag-knowledge.md"));
        }
    }

    public record KnowledgeSection(String title, String excerpt, String source) {
    }
}
