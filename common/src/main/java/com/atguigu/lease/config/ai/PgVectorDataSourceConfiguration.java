package com.atguigu.lease.config.ai;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 向量库(PostgreSQL + pgvector)双数据源与 VectorStore 配置。
 *
 * <p>注意:一旦在容器中存在第二个 {@link DataSource} bean,Spring Boot 的
 * {@code DataSourceAutoConfiguration} 会因 {@code @ConditionalOnMissingBean(DataSource.class)}
 * 而不再自动创建主数据源。因此本类在开启向量库时<b>同时显式定义主数据源(MySQL,@Primary)</b>,
 * 保证 MyBatis-Plus 仍使用 MySQL;向量库走独立的 pg 数据源 + JdbcTemplate + 手动构建的 PgVectorStore。
 *
 * <p>整体条件装配:仅当配置了 {@code app.datasource.pg.url} 时才启用向量库相关 bean;
 * 未配置时主数据源回到 Spring Boot 自动配置,主业务不受影响。
 */
@Configuration
@ConditionalOnProperty(name = "app.datasource.pg.url")
@Conditional(AiModelAvailableCondition.class)
public class PgVectorDataSourceConfiguration {

    // ---------- 主数据源:MySQL(供 MyBatis-Plus 使用) ----------

    @Primary
    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password,
            @Value("${spring.datasource.driver-class-name:com.mysql.cj.jdbc.Driver}") String driverClassName) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName(driverClassName);
        ds.setPoolName("mysql-main-pool");
        return ds;
    }

    // ---------- 向量库数据源:PostgreSQL ----------

    @Bean
    public DataSource pgDataSource(
            @Value("${app.datasource.pg.url}") String url,
            @Value("${app.datasource.pg.username}") String username,
            @Value("${app.datasource.pg.password}") String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setPoolName("pg-vector-pool");
        return ds;
    }

    @Bean
    public JdbcTemplate pgJdbcTemplate(@Qualifier("pgDataSource") DataSource pgDataSource) {
        // 必须用 @Qualifier 指定 pg 源:主数据源 dataSource 是 @Primary,
        // 多候选时 Spring 会优先按 @Primary 注入(先于按参数名),否则会把 MySQL 注进来。
        return new JdbcTemplate(pgDataSource);
    }

    @Bean
    public VectorStore vectorStore(JdbcTemplate pgJdbcTemplate,
                                   EmbeddingModel embeddingModel,
                                   @Value("${spring.ai.vectorstore.pgvector.dimensions:1024}") int dimensions) throws Exception {
        return PgVectorStore.builder(pgJdbcTemplate, embeddingModel)
                .dimensions(dimensions)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(true) // Spring AI 1.0 GA 默认 false,需显式开启以自动建表
                .build();
    }
}
