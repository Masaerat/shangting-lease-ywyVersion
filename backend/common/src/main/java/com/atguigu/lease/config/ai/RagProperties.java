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

    /** 分层记忆中保留原文的最近轮数 */
    private int memoryRecentTurns = 6;

    /** 最近原文允许占用的最大字符数 */
    private int memoryMaxRecentChars = 6000;

    /** 注入 Agent 的记忆上下文最大字符数 */
    private int memoryMaxContextChars = 8000;

    /** 滚动摘要最大字符数 */
    private int memorySummaryMaxChars = 2000;

    /** 每条进入滚动摘要的消息最大字符数 */
    private int memorySummaryItemMaxChars = 240;

    /** 单条最近原文最大字符数 */
    private int memoryMessageMaxChars = 2000;

    /** RRF 常数，越大越弱化头部名次差异 */
    private int rrfK = 60;

    /** 向量召回在 RRF 中的权重 */
    private double vectorWeight = 1.0;

    /** 本地词法召回在 RRF 中的权重 */
    private double lexicalWeight = 0.8;

    /** 分类匹配的归一化加分 */
    private double categoryBoost = 0.08;

    /** 默认 namespace */
    private String namespaceDefault = "default";
}
