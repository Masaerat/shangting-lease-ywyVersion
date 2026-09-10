package com.atguigu.lease.web.app.service.ai.impl;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LocalRentalKnowledgeServiceTest {
    @Test
    void irrelevantQuestionsDoNotReceiveUnrelatedPolicyCitations() {
        var service = new LocalRentalKnowledgeService();
        assertThat(service.search("明天天气如何")).isEmpty();
        assertThat(service.search(null)).isEmpty();
        assertThat(service.search("押金怎么退")).isNotEmpty();
    }
}
