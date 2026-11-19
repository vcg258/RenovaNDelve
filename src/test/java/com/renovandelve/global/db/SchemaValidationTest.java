package com.renovandelve.global.db;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 빈 DB에 Flyway 마이그레이션(V1 ~ 최신, 시드 포함)을 적용한 뒤 Hibernate {@code validate}로 엔티티와 대조한다.
 * 엔티티만 고치고 {@code V{n}__*.sql}을 빠뜨리면 이 테스트가 실패한다. 로컬 DB는 이미 맞춰져 있어 이 누락이 드러나지 않는다.
 * <p>
 * Docker가 없으면 건너뛴다. {@code validate}는 컬럼의 존재와 타입만 보므로 인덱스·제약 누락은 잡지 못한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false"
})
class SchemaValidationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("빈 DB에 마이그레이션을 모두 적용하면 엔티티와 스키마가 맞고, 시드가 들어간다")
    void migrationsMatchEntities() {
        // 컨텍스트가 떴다는 것 자체가 validate 통과다
        assertThat(entityManager.getMetamodel().getEntities()).isNotEmpty();
        Integer basic = jdbcTemplate.queryForObject("SELECT count(*) FROM skill WHERE is_basic", Integer.class);
        assertThat(basic).isEqualTo(1);
    }
}
