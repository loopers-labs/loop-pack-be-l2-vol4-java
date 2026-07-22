package com.loopers;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

// job.name 미지정 시 application.yml 기본값 "NONE" 이 주입되어 jobLauncherApplicationRunner 가
// 존재하지 않는 잡을 실행하려다 컨텍스트 로드가 실패한다. 순수 컨텍스트 검증이므로 잡 자동 실행을 끈다.
@TestPropertySource(properties = "spring.batch.job.name=")
@SpringBootTest
public class CommerceBatchApplicationTest {
    @Test
    void contextLoads() {}
}
