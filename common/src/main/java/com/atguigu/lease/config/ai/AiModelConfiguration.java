package com.atguigu.lease.config.ai;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
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
 * <p>base-url / api-key / 模型名仍从 {@code spring.ai.openai.*} 读取(yml 为唯一来源);
 * 路径默认为 GLM 风格,可通过 {@code spring.ai.openai.completions-path} /
 * {@code spring.ai.openai.embeddings-path} 覆盖以兼容其他厂商(如标准 OpenAI 用 {@code /v1/chat/completions})。
 *
 * <p>定义本类的 OpenAiChatModel / OpenAiEmbeddingModel bean 后,Spring AI 的自动配置
 * (均带 @ConditionalOnMissingBean)会自动退避,不会产生冲突。
 */
@Configuration
@ConditionalOnProperty(name = "spring.ai.openai.api-key")
public class AiModelConfiguration {

    @Bean
    public OpenAiApi openAiApi(
            @Value("${spring.ai.openai.base-url}") String baseUrl,
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${spring.ai.openai.completions-path:/chat/completions}") String completionsPath,
            @Value("${spring.ai.openai.embeddings-path:/embeddings}") String embeddingsPath) {
        return OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .completionsPath(completionsPath)
                .embeddingsPath(embeddingsPath)
                .build();
    }

    @Bean
    public OpenAiChatModel openAiChatModel(OpenAiApi openAiApi,
            @Value("${spring.ai.openai.chat.options.model:glm-4-flash}") String chatModel) {
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder().model(chatModel).build())
                .build();
    }

    @Bean
    public OpenAiEmbeddingModel openAiEmbeddingModel(OpenAiApi openAiApi,
            @Value("${spring.ai.openai.embedding.options.model:embedding-3}") String embedModel) {
        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder().model(embedModel).build());
    }
}
