package com.loopers.concurrency;

import com.loopers.testcontainers.MySqlTestContainersConfig;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;

/**
 * 동시성 테스트 전용 Spring Boot 부트스트랩.
 * <p>
 * 프로덕션 {@code CommerceApiApplication} 의 component scan 은 {@code modules/jpa/src/testFixtures}
 * 의 {@link MySqlTestContainersConfig} 를 끌어와 Docker 호환성이 깨진 환경에서 static initializer
 * 가 실행되며 ApplicationContext 초기화가 실패한다.
 * <p>
 * 본 클래스는 H2 in-memory 기반 동시성 테스트만을 위해 testcontainers Configuration 을 명시적으로
 * 제외한다 — REGEX 패턴 매칭이 환경별로 불안정할 수 있어 클래스 참조(ASSIGNABLE_TYPE) 로 고정.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
    basePackages = "com.loopers",
    excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = MySqlTestContainersConfig.class)
)
public class ConcurrencyTestApplication {
}
