package com.atguigu.lease.config.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 相关可调参数,映射 app.ai.rag.*。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.ai.rag")
public class RagProperties {

    /** 分片目标 token 数 */
    private int chunkSize = 800;

    /** 分片最小字符数(TokenTextSplitter minChunkSizeChars) */
    private int minChunkSizeChars = 350;

    /** 小于此长度的分片丢弃(TokenTextSplitter minChunkLengthToEmbed) */
    private int minChunkLengthToEmbed = 5;

    /** 单文档最大分片数(TokenTextSplitter maxNumChunks) */
    private int maxNumChunks = 10000;

    /** 检索 top-k */
    private int topK = 5;

    /** 相似度阈值 */
    private double similarityThreshold = 0.75;

    /** 多轮对话历史保留轮数 */
    private int historyTurns = 10;

    /** 默认 namespace */
    private String namespaceDefault = "default";
}
