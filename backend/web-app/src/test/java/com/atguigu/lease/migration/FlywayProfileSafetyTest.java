package com.atguigu.lease.migration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayProfileSafetyTest {

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Test
    void localDefaultBaselinesExistingSchemaBeforeApplyingIncrementalMigrations() throws IOException {
        assertThat(property("application-default.yml", "spring.flyway.enabled")).isEqualTo(true);
        assertThat(property("application-default.yml", "spring.flyway.baseline-on-migrate")).isEqualTo(true);
        assertThat(property("application-default.yml", "spring.flyway.baseline-version")).isEqualTo(1);
    }

    @Test
    void dockerProfileRunsMigrationsButRefusesToBaselineAnExistingDatabase() throws IOException {
        assertThat(property("application-docker.yml", "spring.flyway.enabled")).isEqualTo(true);
        assertThat(property("application-docker.yml", "spring.flyway.baseline-on-migrate")).isEqualTo(false);
    }

    private Object property(String resourceName, String propertyName) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourceName);
        assertThat(resource.exists()).as(resourceName + " must exist").isTrue();
        List<PropertySource<?>> sources = loader.load(resourceName, resource);
        return sources.stream()
                .map(source -> source.getProperty(propertyName))
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
    }
}
