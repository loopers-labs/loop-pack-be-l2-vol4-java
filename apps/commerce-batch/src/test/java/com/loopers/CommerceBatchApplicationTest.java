package com.loopers;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 잡 없이 컨텍스트만 확인한다. 배치 앱은 spring.batch.job.name 기본값이 NONE 이라
 * 러너를 켜 두면 "No job found with name 'NONE'" 으로 기동이 실패한다.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.batch.job.enabled=false")
public class CommerceBatchApplicationTest {
    @Test
    void contextLoads() {}
}
