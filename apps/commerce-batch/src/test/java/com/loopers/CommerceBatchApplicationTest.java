package com.loopers;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "spring.batch.job.enabled=false") // job.name 기본값(NONE) 자동 실행을 끈다. 컨텍스트 로드만 검증
public class CommerceBatchApplicationTest {
    @Test
    void contextLoads() {}
}
