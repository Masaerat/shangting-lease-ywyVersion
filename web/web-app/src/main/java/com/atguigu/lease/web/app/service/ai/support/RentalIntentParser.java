package com.atguigu.lease.web.app.service.ai.support;

import com.atguigu.lease.web.app.service.ai.model.RentalIntent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RentalIntentParser {

    private static final Pattern RENT_PATTERN = Pattern.compile("(\\d{3,5})\\s*(?:元|块|rmb|RMB)?");

    public RentalIntent parse(String message) {
        RentalIntent intent = new RentalIntent();
        String normalized = message == null ? "" : message.trim().toLowerCase();

        boolean asksRoom = containsAny(normalized, "找房", "房子", "房源", "公寓", "租房", "一室", "两室", "地铁");
        boolean asksKnowledge = containsAny(normalized, "押金", "退租", "续租", "合同", "水电", "物业", "维修", "预约", "违约", "入住");

        intent.setRoomSearchRequested(asksRoom);
        intent.setRentalQuestionRequested(asksKnowledge || !asksRoom);

        Matcher matcher = RENT_PATTERN.matcher(normalized);
        if (matcher.find()) {
            BigDecimal rent = new BigDecimal(matcher.group(1));
            intent.setMinRent(rent.multiply(new BigDecimal("0.8")));
            intent.setMaxRent(rent.multiply(new BigDecimal("1.2")));
            intent.setRoomSearchRequested(true);
        }

        return intent;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
