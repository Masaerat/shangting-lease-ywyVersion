package com.atguigu.lease.web.admin.service.ai.impl;

import com.atguigu.lease.config.ai.RagProperties;
import com.atguigu.lease.model.entity.AiKnowledgeDoc;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StructuredKnowledgeChunker {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final TokenTextSplitter splitter;

    public StructuredKnowledgeChunker(RagProperties properties) {
        this.splitter = new TokenTextSplitter(
                properties.getChunkSize(),
                properties.getMinChunkSizeChars(),
                properties.getMinChunkLengthToEmbed(),
                properties.getMaxNumChunks(),
                true);
    }

    public TokenTextSplitter splitter() {
        return splitter;
    }

    public List<Document> split(AiKnowledgeDoc source, List<Document> rawDocuments) {
        if (source == null || source.getId() == null) {
            throw new IllegalArgumentException("Knowledge document id is required");
        }
        List<Section> sections = new ArrayList<>();
        for (Document raw : rawDocuments == null ? List.<Document>of() : rawDocuments) {
            sections.addAll(sections(raw.getText(), source.getDocName()));
        }

        List<Document> chunks = new ArrayList<>();
        int sectionIndex = 0;
        for (Section section : sections) {
            Map<String, Object> metadata = metadata(source, section);
            List<Document> split = splitter.split(List.of(new Document(section.content(), metadata)));
            for (int chunkIndex = 0; chunkIndex < split.size(); chunkIndex++) {
                Document chunk = split.get(chunkIndex);
                String checksum = checksum(chunk.getText());
                Map<String, Object> chunkMetadata = new HashMap<>(chunk.getMetadata());
                chunkMetadata.put("chunkId", chunkId(source.getId(), sectionIndex, chunkIndex, checksum));
                chunkMetadata.put("chunkIndex", chunkIndex);
                chunkMetadata.put("checksum", checksum);
                chunks.add(new Document(
                        chunkMetadata.get("chunkId").toString(), chunk.getText(), Map.copyOf(chunkMetadata)));
            }
            sectionIndex++;
        }
        return List.copyOf(chunks);
    }

    private List<Section> sections(String text, String documentName) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<Section> result = new ArrayList<>();
        String chapter = documentName == null ? "正文" : documentName;
        String section = "正文";
        StringBuilder content = new StringBuilder();
        for (String line : text.split("\\R")) {
            Matcher heading = HEADING.matcher(line.trim());
            if (heading.matches()) {
                add(result, chapter, section, content);
                String title = heading.group(2).trim();
                if (heading.group(1).length() == 1) {
                    chapter = title;
                    section = title;
                } else {
                    section = title;
                }
            } else if (!line.isBlank()) {
                if (!content.isEmpty()) {
                    content.append('\n');
                }
                content.append(line.trim());
            }
        }
        add(result, chapter, section, content);
        return result;
    }

    private void add(List<Section> result, String chapter, String section, StringBuilder content) {
        if (!content.isEmpty()) {
            result.add(new Section(chapter, section, content.toString()));
            content.setLength(0);
        }
    }

    private Map<String, Object> metadata(AiKnowledgeDoc source, Section section) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("namespace", blankDefault(source.getNamespace(), "default"));
        metadata.put("docType", "doc");
        metadata.put("docId", source.getId());
        metadata.put("documentName", blankDefault(source.getDocName(), "unknown"));
        metadata.put("source", blankDefault(source.getDocName(), "unknown"));
        metadata.put("chapter", section.chapter());
        metadata.put("section", section.section());
        metadata.put("category", category(section.chapter() + " " + section.section() + " " + section.content()));
        metadata.put("version", 1);
        metadata.put("effectiveDate", source.getCreateTime() == null ? ""
                : source.getCreateTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().format(DATE));
        return metadata;
    }

    private String category(String text) {
        String value = text.toLowerCase(Locale.ROOT);
        if (value.contains("押金")) return "DEPOSIT";
        if (value.contains("付款") || value.contains("月付") || value.contains("季付")) return "PAYMENT";
        if (value.contains("预约") || value.contains("看房")) return "APPOINTMENT";
        if (value.contains("报修") || value.contains("维修")) return "REPAIR";
        if (value.contains("退租") || value.contains("结算") || value.contains("违约")) return "CHECKOUT";
        if (value.contains("入住") || value.contains("材料")) return "MOVE_IN";
        return "GENERAL";
    }

    private String chunkId(Long docId, int sectionIndex, int chunkIndex, String checksum) {
        return "knowledge-" + docId + "-" + sectionIndex + "-" + chunkIndex + "-" + checksum.substring(0, 12);
    }

    private String checksum(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record Section(String chapter, String section, String content) {
    }
}
