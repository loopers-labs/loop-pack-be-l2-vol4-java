package com.loopers.concurrency;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 동시성 테스트 공통 베이스 — H2 in-memory + 비관적 락 검증용.
 * <p>
 * 1주차에서 도입된 USE_LOCAL_MYSQL=true 회피 옵션이 ApplicationContext 폭발은 막지만,
 * MySqlTestContainersConfig 의 static block 이 datasource System property 를 MySQL 로 고정한다.
 * {@link DynamicPropertySource} 는 System property 보다 우선이므로 여기서 H2 로 강제 override 한다.
 */
@SpringBootTest(classes = ConcurrencyTestApplication.class)
public abstract class AbstractH2ConcurrencyTest {

    @DynamicPropertySource
    static void overrideDatasourceToH2(DynamicPropertyRegistry registry) {
        registry.add("datasource.mysql-jpa.main.driver-class-name", () -> "org.h2.Driver");
        registry.add("datasource.mysql-jpa.main.jdbc-url", () ->
            "jdbc:h2:mem:concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000;"
                + "NON_KEYWORDS=USER,VALUE,KEY;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE");
        registry.add("datasource.mysql-jpa.main.username", () -> "sa");
        registry.add("datasource.mysql-jpa.main.password", () -> "");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.H2Dialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.generate-ddl", () -> "true");
        registry.add("spring.jpa.show-sql", () -> "false");
    }
}
