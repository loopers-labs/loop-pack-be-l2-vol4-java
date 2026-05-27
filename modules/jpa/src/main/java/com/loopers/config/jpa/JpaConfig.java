package com.loopers.config.jpa;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@EntityScan({"com.loopers"})
// [fix] Domain-First 리팩토링 이후 JPA 리포지토리 스캔 경로가
//       com.loopers.infrastructure → com.loopers.*.infrastructure로 변경됐으나
//       JpaConfig에 반영되지 않아 모든 통합 테스트 컨텍스트 로딩 실패
@EnableJpaRepositories({"com.loopers"})
public class JpaConfig {
}
