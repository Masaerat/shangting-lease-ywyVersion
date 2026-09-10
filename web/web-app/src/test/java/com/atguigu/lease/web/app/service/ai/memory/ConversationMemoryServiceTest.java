package com.atguigu.lease.web.app.service.ai.memory;

import com.atguigu.lease.common.utils.CacheUtil;
import com.atguigu.lease.common.utils.JsonUtil;
import com.atguigu.lease.config.ai.RagProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationMemoryServiceTest {

    @Test
    void compressedEarlyTurnKeepsStructuredConstraintsAndRollingSummary() {
        RagProperties properties = new RagProperties();
        properties.setMemoryRecentTurns(1);
        MemoryFixture fixture = fixture(properties);

        fixture.service().append(7L, "conv", "我想在上海浦东新区整租，预算3000元以内，最好靠近地铁", "收到");
        fixture.service().append(7L, "conv", "再看看其他房源", "找到两套");

        List<String> context = fixture.service().loadContext(7L, "conv");
        assertThat(context).anyMatch(line -> line.contains("maxRent") && line.contains("3000"));
        assertThat(context).anyMatch(line -> line.contains("city") && line.contains("上海"));
        assertThat(context).anyMatch(line -> line.contains("district") && line.contains("浦东新区"));
        assertThat(context).anyMatch(line -> line.startsWith("较早对话摘要:") && line.contains("预算3000"));
        assertThat(context).contains("用户:再看看其他房源", "顾问:找到两套");
    }

    @Test
    void latestExplicitBudgetCorrectionOverridesEarlierValue() {
        MemoryFixture fixture = fixture(new RagProperties());
        fixture.service().append(7L, "conv", "预算不超过3000元", "收到");
        fixture.service().append(7L, "conv", "预算改成3500元", "已更新");

        ConversationMemory stored = JsonUtil.parseObject(fixture.stored().get(), ConversationMemory.class);
        assertThat(stored.state()).containsEntry("maxRent", "3500");
        assertThat(stored.revision()).isEqualTo(2);
    }

    @Test
    void explicitUnlimitedBudgetClearsTheOldConstraintWithoutInventingACity() {
        MemoryFixture fixture = fixture(new RagProperties());
        fixture.service().append(7L, "conv", "我想在上海租房，预算3000元以内", "收到");
        fixture.service().append(7L, "conv", "预算不限，看看这个小区的房子", "好的");

        ConversationMemory stored = JsonUtil.parseObject(fixture.stored().get(), ConversationMemory.class);
        assertThat(stored.state()).containsEntry("city", "上海")
                .doesNotContainKeys("minRent", "maxRent", "district");
    }

    @Test
    void legacyJsonArrayIsMigratedWithoutBreakingMultilineMessage() {
        CacheUtil cache = mock(CacheUtil.class);
        when(cache.get(anyString(), eq(String.class))).thenReturn(JsonUtil.toJsonString(List.of(
                "用户:第一行\n第二行", "顾问:已经记录")));
        ConversationMemoryService service = new ConversationMemoryService(cache, new RagProperties());

        assertThat(service.loadContext(7L, "conv"))
                .contains("用户:第一行\n第二行", "顾问:已经记录");
    }

    @Test
    void cacheFailureNeverBreaksTheChatPath() {
        CacheUtil cache = mock(CacheUtil.class);
        when(cache.get(anyString(), eq(String.class))).thenThrow(new IllegalStateException("redis down"));
        doThrow(new IllegalStateException("redis down")).when(cache)
                .set(anyString(), any(), anyLong(), any());
        ConversationMemoryService service = new ConversationMemoryService(cache, new RagProperties());

        assertThat(service.loadContext(7L, "conv")).isEmpty();
        assertThatCode(() -> service.append(7L, "conv", "继续", "好的")).doesNotThrowAnyException();
    }

    @Test
    void mostRecentTurnSurvivesAnAggressiveCharacterBudget() {
        RagProperties properties = new RagProperties();
        properties.setMemoryRecentTurns(10);
        properties.setMemoryMaxRecentChars(200);
        MemoryFixture fixture = fixture(properties);
        fixture.service().append(7L, "conv", "第一轮".repeat(40), "第一答".repeat(40));
        fixture.service().append(7L, "conv", "最新问题", "最新答案");

        assertThat(fixture.service().loadContext(7L, "conv"))
                .contains("用户:最新问题", "顾问:最新答案")
                .anyMatch(line -> line.startsWith("较早对话摘要:"));
    }

    private MemoryFixture fixture(RagProperties properties) {
        CacheUtil cache = mock(CacheUtil.class);
        AtomicReference<String> stored = new AtomicReference<>();
        when(cache.get(anyString(), eq(String.class))).thenAnswer(invocation -> stored.get());
        doAnswer(invocation -> {
            stored.set(invocation.getArgument(1));
            return null;
        }).when(cache).set(anyString(), any(), anyLong(), any());
        return new MemoryFixture(new ConversationMemoryService(cache, properties), stored);
    }

    private record MemoryFixture(ConversationMemoryService service, AtomicReference<String> stored) {
    }
}
