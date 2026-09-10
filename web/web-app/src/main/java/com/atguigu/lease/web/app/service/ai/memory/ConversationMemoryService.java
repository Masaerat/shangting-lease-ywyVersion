package com.atguigu.lease.web.app.service.ai.memory;

import com.atguigu.lease.common.constant.AiRedisConstant;
import com.atguigu.lease.common.utils.CacheUtil;
import com.atguigu.lease.common.utils.JsonUtil;
import com.atguigu.lease.config.ai.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds bounded hierarchical memory: durable user state, a rolling summary and recent verbatim turns.
 * It intentionally does not call the chat model, so compression cannot add latency or invent business facts.
 */
@Service
public class ConversationMemoryService {

    private static final Logger LOG = LoggerFactory.getLogger(ConversationMemoryService.class);
    private static final Pattern RANGE = Pattern.compile("(\\d{3,6})\\s*(?:元)?\\s*(?:-|到|至|~|～)\\s*(\\d{3,6})");
    private static final Pattern MAX_RENT = Pattern.compile("(?:预算|不超过|最高|最多|月租)[^\\d]{0,8}(\\d{3,6})");
    private static final Pattern MAX_RENT_SUFFIX = Pattern.compile("(\\d{3,6})\\s*(?:元)?\\s*(?:以内|以下)");
    private static final Pattern MIN_RENT = Pattern.compile("(?:至少|最低|起租)[^\\d]{0,8}(\\d{3,6})");
    private static final Pattern CITY = Pattern.compile("(?:在|城市(?:是|为)?|考虑|想去)(北京|上海|天津|重庆|[\\p{IsHan}]{2,6}市)");
    private static final Pattern DISTRICT = Pattern.compile("(?:在|位于|区域(?:是|为)?|考虑)?([\\p{IsHan}]{2,8}?(?:新区|区|县))");
    private static final Pattern ROOM_ID = Pattern.compile("(?:roomId|房源(?:编号|ID|id|号)?|房间(?:编号|ID|id|号)?)[：:#\\s]*([0-9]{1,18})(?![0-9])");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");
    private static final Object[] LOCKS = new Object[64];

    static {
        for (int index = 0; index < LOCKS.length; index++) LOCKS[index] = new Object();
    }

    private final CacheUtil cacheUtil;
    private final RagProperties properties;

    public ConversationMemoryService(CacheUtil cacheUtil, RagProperties properties) {
        this.cacheUtil = cacheUtil;
        this.properties = properties;
    }

    public String key(Long userId, String conversationId) {
        return AiRedisConstant.CHAT_HISTORY_PREFIX + userId + ":" + conversationId;
    }

    public List<String> loadContext(Long userId, String conversationId) {
        try {
            return render(read(userId, conversationId));
        } catch (RuntimeException error) {
            LOG.warn("Conversation memory unavailable: {}", error.getClass().getSimpleName());
            return List.of();
        }
    }

    public void append(Long userId, String conversationId, String userMessage, String assistantMessage) {
        String key = key(userId, conversationId);
        synchronized (lockFor(key)) {
            try {
                ConversationMemory current = read(userId, conversationId);
                ConversationMemory updated = append(current, userMessage, assistantMessage);
                cacheUtil.set(key, JsonUtil.toJsonString(updated),
                        AiRedisConstant.CHAT_HISTORY_TTL_SEC, TimeUnit.SECONDS);
            } catch (RuntimeException error) {
                // Memory is optional and must never cause completed tools to execute a second time.
                LOG.warn("Conversation memory save failed: {}", error.getClass().getSimpleName());
            }
        }
    }

    ConversationMemory append(ConversationMemory current, String userMessage, String assistantMessage) {
        ConversationMemory base = current == null ? ConversationMemory.empty() : current;
        Map<String, String> state = new LinkedHashMap<>(base.state());
        updateState(state, userMessage == null ? "" : userMessage);

        List<ConversationMemory.ConversationMessage> recent = new ArrayList<>(base.recentMessages());
        recent.add(message("user", userMessage));
        recent.add(message("assistant", assistantMessage));

        String summary = base.summary();
        int maxMessages = Math.max(2, properties.getMemoryRecentTurns() * 2);
        while (recent.size() > maxMessages
                || (recent.size() > 2 && recentCharacters(recent) > properties.getMemoryMaxRecentChars())) {
            int count = Math.min(2, recent.size());
            List<ConversationMemory.ConversationMessage> removed = new ArrayList<>(recent.subList(0, count));
            recent.subList(0, count).clear();
            summary = appendSummary(summary, removed);
        }
        return new ConversationMemory(
                ConversationMemory.CURRENT_SCHEMA_VERSION,
                base.revision() + 1,
                summary,
                state,
                recent);
    }

    ConversationMemory read(Long userId, String conversationId) {
        String raw = cacheUtil.get(key(userId, conversationId), String.class);
        if (raw == null || raw.isBlank()) return ConversationMemory.empty();
        String trimmed = raw.trim();
        if (trimmed.startsWith("{")) {
            return JsonUtil.parseObject(trimmed, ConversationMemory.class);
        }
        List<String> legacy;
        if (trimmed.startsWith("[")) {
            legacy = List.of(JsonUtil.parseObject(trimmed, String[].class));
        } else {
            legacy = trimmed.lines().filter(line -> !line.isBlank()).toList();
        }
        return migrateLegacy(legacy);
    }

    private ConversationMemory migrateLegacy(List<String> lines) {
        ConversationMemory memory = ConversationMemory.empty();
        String pendingUser = null;
        for (String line : lines) {
            if (line.startsWith("用户:")) {
                if (pendingUser != null) memory = append(memory, pendingUser, "");
                pendingUser = line.substring("用户:".length());
            } else if (line.startsWith("顾问:")) {
                memory = append(memory, pendingUser == null ? "" : pendingUser, line.substring("顾问:".length()));
                pendingUser = null;
            }
        }
        if (pendingUser != null) memory = append(memory, pendingUser, "");
        return memory;
    }

    private List<String> render(ConversationMemory memory) {
        List<String> prefix = new ArrayList<>();
        if (!memory.state().isEmpty()) {
            prefix.add("已确认用户条件（当前用户的新表述优先）:" + JsonUtil.toJsonString(memory.state()));
        }
        if (!memory.summary().isBlank()) prefix.add("较早对话摘要:" + memory.summary());

        int budget = Math.max(1000, properties.getMemoryMaxContextChars());
        int used = prefix.stream().mapToInt(String::length).sum();
        List<String> recent = new ArrayList<>();
        List<ConversationMemory.ConversationMessage> messages = memory.recentMessages();
        for (int index = messages.size() - 1; index >= 0; index--) {
            ConversationMemory.ConversationMessage message = messages.get(index);
            String rendered = ("user".equals(message.role()) ? "用户:" : "顾问:") + message.content();
            if (!recent.isEmpty() && used + rendered.length() > budget) break;
            recent.add(rendered);
            used += rendered.length();
        }
        Collections.reverse(recent);
        prefix.addAll(recent);
        return List.copyOf(prefix);
    }

    private void updateState(Map<String, String> state, String message) {
        if (containsAny(message, "预算不限", "没有预算限制", "不限制预算")) {
            state.remove("minRent");
            state.remove("maxRent");
        }
        if (containsAny(message, "城市不限", "地点不限")) {
            state.remove("city");
            state.remove("district");
        } else if (containsAny(message, "区域不限", "地区不限")) {
            state.remove("district");
        }
        Matcher range = RANGE.matcher(message);
        if (range.find()) {
            state.put("minRent", range.group(1));
            state.put("maxRent", range.group(2));
        } else {
            putGroup(state, "maxRent", firstMatch(MAX_RENT, MAX_RENT_SUFFIX, message));
            putGroup(state, "minRent", group(MIN_RENT, message));
        }
        putGroup(state, "city", group(CITY, message));
        String district = group(DISTRICT, message);
        if (district != null && !district.endsWith("小区")) {
            String city = state.get("city");
            if (city != null && district.startsWith(city) && district.length() > city.length()) {
                district = district.substring(city.length());
            }
            state.put("district", district);
        }
        putGroup(state, "selectedRoomId", group(ROOM_ID, message));
        putGroup(state, "contactPhone", group(PHONE, message));

        if (containsAny(message, "不要合租", "不接受合租", "不能合租", "只要整租") || message.contains("整租")) {
            state.put("rentMode", "WHOLE");
        } else if (message.contains("合租")) {
            state.put("rentMode", "SHARED");
        }
        updateBooleanPreference(state, "nearSubway", message,
                new String[]{"不要地铁", "不考虑地铁", "不需要靠近地铁"},
                new String[]{"地铁", "轨道交通"});
        updateBooleanPreference(state, "petFriendly", message, new String[]{"不能养宠物", "不养宠物", "没有宠物"},
                new String[]{"养猫", "养狗", "宠物"});
        updateBooleanPreference(state, "elevatorRequired", message, new String[]{"不需要电梯"},
                new String[]{"电梯"});
    }

    private void updateBooleanPreference(Map<String, String> state, String key, String message,
                                         String[] negative, String[] positive) {
        if (containsAny(message, negative)) state.put(key, "false");
        else if (containsAny(message, positive)) state.put(key, "true");
    }

    private String appendSummary(String existing, List<ConversationMemory.ConversationMessage> removed) {
        StringBuilder addition = new StringBuilder();
        for (ConversationMemory.ConversationMessage message : removed) {
            if (message.content().isBlank()) continue;
            if (!addition.isEmpty()) addition.append('；');
            addition.append("user".equals(message.role()) ? "用户：" : "顾问：")
                    .append(normalize(message.content()));
        }
        if (addition.isEmpty()) return existing;
        String combined = existing == null || existing.isBlank()
                ? addition.toString() : existing + " | " + addition;
        return boundedSummary(combined, Math.max(200, properties.getMemorySummaryMaxChars()));
    }

    private String boundedSummary(String value, int maxChars) {
        if (value.length() <= maxChars) return value;
        int head = maxChars / 2;
        int tail = maxChars - head - 5;
        return value.substring(0, head) + " ... " + value.substring(value.length() - tail);
    }

    private ConversationMemory.ConversationMessage message(String role, String content) {
        String normalized = content == null ? "" : content;
        int limit = Math.max(200, properties.getMemoryMessageMaxChars());
        if (normalized.length() > limit) normalized = normalized.substring(0, limit) + "…";
        return new ConversationMemory.ConversationMessage(role, normalized);
    }

    private int recentCharacters(List<ConversationMemory.ConversationMessage> messages) {
        return messages.stream().mapToInt(item -> item.content().length()).sum();
    }

    private String normalize(String value) {
        String normalized = value.replaceAll("\\s+", " ").trim();
        int max = Math.max(80, properties.getMemorySummaryItemMaxChars());
        return normalized.length() <= max ? normalized : normalized.substring(0, max) + "…";
    }

    private String firstMatch(Pattern first, Pattern second, String value) {
        String result = group(first, value);
        return result == null ? group(second, value) : result;
    }

    private String group(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    private void putGroup(Map<String, String> state, String key, String value) {
        if (value != null && !value.isBlank()) state.put(key, value);
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }

    private Object lockFor(String key) {
        return LOCKS[(key.hashCode() & Integer.MAX_VALUE) % LOCKS.length];
    }
}
