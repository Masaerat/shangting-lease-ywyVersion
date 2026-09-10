package com.atguigu.lease.config.ai;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 模型 bean 配置(OpenAI 兼容协议接 GLM / 智谱)。
 *
 * <p><b>为何不直接用 starter 的自动配置:</b>GLM 的接口挂在
 * {@code /api/paas/v4/<chat/completions|embeddings>} 下(版本号 v4 在 base-url 里);
 * 而 Spring AI 的 OpenAI 自动配置默认拼接 {@code /v1/chat/completions}、{@code /v1/embeddings},
 * 与 base-url 拼接后凑成 {@code /v4/v1/...} 导致 404。
 * 故手动构建 {@link OpenAiApi} 并指定 GLM 原生路径 {@code /chat/completions}、{@code /embeddings}。
 *
 * <p><b>双 provider 支持:</b>chat 与 embedding 可分别使用不同的 Base URL、API Key 和接口路径。
 * 典型场景:chat 走 OpenAI,embedding 走智谱 GLM。
 * <ul>
 *   <li>chat 用 {@code spring.ai.openai.api-key}(主 key);</li>
 *   <li>embedding 用 {@code spring.ai.openai.embedding.api-key},<b>缺省回退到主 key</b>
 *       (只配一个 key 时 chat/embedding 共用,行为与单 key 一致)。</li>
 * </ul>
 *
 * <p>base-url / 模型名 / 路径均从 {@code spring.ai.openai.*} 读取(yml 为唯一来源)。
 * provider 专属属性未配置时会回退到原有的公共属性,兼容历史单网关配置。
 *
 * <p>定义本类的 OpenAiChatModel / OpenAiEmbeddingModel bean 后,Spring AI 的自动配置
 * (均带 @ConditionalOnMissingBean)会自动退避,不会产生冲突。
 */
@Configuration
@Conditional(AiModelAvailableCondition.class)
public class AiModelConfiguration {

    /** chat 专用 OpenAiApi(主 key)。 */
    @Bean
    public OpenAiApi chatOpenAiApi(
            @Value("${spring.ai.openai.chat.base-url:${spring.ai.openai.base-url}}") String baseUrl,
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${spring.ai.openai.chat.completions-path:${spring.ai.openai.completions-path:/chat/completions}}") String completionsPath,
            @Value("${spring.ai.openai.embeddings-path:/embeddings}") String embeddingsPath) {
        return OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .completionsPath(completionsPath)
                .embeddingsPath(embeddingsPath)
                .build();
    }

    /** embedding 专用 OpenAiApi(独立 key,缺省回退主 key)。 */
    @Bean
    public OpenAiApi embeddingOpenAiApi(
            @Value("${spring.ai.openai.embedding.base-url:${spring.ai.openai.base-url}}") String baseUrl,
            // 嵌套占位符:未配 embedding.api-key 时回退到主 api-key
            @Value("${spring.ai.openai.embedding.api-key:${spring.ai.openai.api-key}}") String embedApiKey,
            @Value("${spring.ai.openai.completions-path:/chat/completions}") String completionsPath,
            @Value("${spring.ai.openai.embedding.embeddings-path:${spring.ai.openai.embeddings-path:/embeddings}}") String embeddingsPath) {
        return OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(embedApiKey)
                .completionsPath(completionsPath)
                .embeddingsPath(embeddingsPath)
                .build();
    }

    @Bean
    public OpenAiChatModel openAiChatModel(@Qualifier("chatOpenAiApi") OpenAiApi chatOpenAiApi,
            @Value("${spring.ai.openai.chat.options.model:glm-4-flash}") String chatModel) {
        return OpenAiChatModel.builder()
                .openAiApi(chatOpenAiApi)
                .defaultOptions(OpenAiChatOptions.builder().model(chatModel).build())
                .build();
    }

    @Bean
    public OpenAiEmbeddingModel openAiEmbeddingModel(@Qualifier("embeddingOpenAiApi") OpenAiApi embeddingOpenAiApi,
            @Value("${spring.ai.openai.embedding.options.model:embedding-3}") String embedModel,
            @Value("${spring.ai.openai.embedding.options.dimensions:1024}") int embedDimensions) {
        // GLM embedding-3 默认返回 2048 维;必须显式指定 dimensions 与 pgvector 列维度一致(此处默认 1024),
        // 否则向量维度 ≠ 列维度,INSERT 进 vector_store 会失败。
        return new OpenAiEmbeddingModel(embeddingOpenAiApi, MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder().model(embedModel).dimensions(embedDimensions).build());
    }
}
