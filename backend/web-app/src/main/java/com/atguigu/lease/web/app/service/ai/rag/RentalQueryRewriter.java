package com.atguigu.lease.web.app.service.ai.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class RentalQueryRewriter {

    public RewrittenQuery rewrite(String question, String requestedCategory) {
        String original = question == null ? "" : question.trim();
        String category = normalizeCategory(requestedCategory);
        if (category == null) {
            category = detectCategory(original);
        }
        Set<String> terms = new LinkedHashSet<>();
        terms.addAll(keywords(original));
        terms.addAll(categoryTerms(category));
        String rewritten = terms.isEmpty() ? original : original + " " + String.join(" ", terms);
        return new RewrittenQuery(original, rewritten.trim(), category, List.copyOf(terms));
    }

    private String detectCategory(String text) {
        if (containsAny(text, "押金", "保证金", "退押", "押一付", "担保金")) return "DEPOSIT";
        if (containsAny(text, "付款", "月付", "季付", "租金", "分期", "交租")) return "PAYMENT";
        if (containsAny(text, "预约", "看房", "到访", "实地看看", "线下看", "参观房子")) return "APPOINTMENT";
        if (containsAny(text, "报修", "维修", "故障", "坏了", "漏水", "停电", "修理")) return "REPAIR";
        if (containsAny(text, "退租", "结算", "违约", "钥匙", "搬走", "不租了", "解约")) return "CHECKOUT";
        if (containsAny(text, "入住", "材料", "搬入", "搬家", "证件", "身份证")) return "MOVE_IN";
        return null;
    }

    private List<String> keywords(String text) {
        List<String> result = new ArrayList<>();
        for (String word : List.of("押金", "付款", "月付", "季付", "预约", "看房",
                "报修", "维修", "退租", "结算", "违约", "入住", "材料", "保证金",
                "分期", "交租", "漏水", "停电", "搬走", "解约", "证件", "身份证")) {
            if (text.contains(word)) {
                result.add(word);
            }
        }
        return result;
    }

    private List<String> categoryTerms(String category) {
        if (category == null) return List.of();
        return switch (category) {
            case "DEPOSIT" -> List.of("押金", "退还条件", "费用结算");
            case "PAYMENT" -> List.of("付款方式", "月付", "季付");
            case "APPOINTMENT" -> List.of("预约看房", "到访时间");
            case "REPAIR" -> List.of("报修服务", "维修");
            case "CHECKOUT" -> List.of("退租流程", "验房", "费用结算");
            case "MOVE_IN" -> List.of("入住材料", "搬入");
            default -> List.of();
        };
    }

    private String normalizeCategory(String category) {
        return category == null || category.isBlank() ? null : category.trim().toUpperCase(Locale.ROOT);
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) return true;
        }
        return false;
    }

    public record RewrittenQuery(String original, String rewritten, String category, List<String> terms) {
    }
}
