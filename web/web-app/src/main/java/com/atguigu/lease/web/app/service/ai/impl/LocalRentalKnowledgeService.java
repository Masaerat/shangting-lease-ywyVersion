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
        if (title.contains("押金")) {
            return containsAny(query, "押金", "保证金", "退押");
        }
        if (title.contains("付款")) {
            return containsAny(query, "付款", "月付", "季付", "租金");
        }
        if (title.contains("预约")) {
            return containsAny(query, "预约", "看房", "到访");
        }
        if (title.contains("报修")) {
            return containsAny(query, "报修", "维修", "故障");
        }
        if (title.contains("退租")) {
            return containsAny(query, "退租", "结算", "违约", "钥匙");
        }
        return false;
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if (value.contains(term)) return true;
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
