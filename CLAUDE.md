# CLAUDE.md — loopers-java-spring-template

## 프로젝트

Java 21 + Spring Boot 3.4.4 기반 멀티 모듈 이커머스 백엔드.
- Group ID: `com.loopers` / Build: Gradle 8.13 (Kotlin DSL)
- 주요 인프라: MySQL 8.0, Redis 7.0 (master/replica), Kafka 3.5.1 KRaft

---

## 핵심 명령어

```shell
# 인프라 실행
docker-compose -f ./docker/infra-compose.yml up

# 테스트
./gradlew :apps:commerce-api:test

# 빌드
./gradlew :apps:commerce-api:bootJar
```

---

## 모듈 구조

```
apps/commerce-api       # REST API (BootJar)
apps/commerce-batch     # Spring Batch
apps/commerce-streamer  # Kafka Consumer
modules/jpa|redis|kafka # 재사용 인프라 설정 (java-library)
supports/               # 횡단 관심사 (logging, monitoring, jackson)
```

의존 규칙: `apps` → `modules`/`supports`. apps 간 의존 금지.

---

## 아키텍처 & 패키지 구성

@docs/architecture.md

---

## 개발 규칙

@docs/dev-rules.md

---

## 주의사항

@docs/cautions.md

---

## 테스트 규칙

- 프로파일: `spring.profiles.active=test`
- Testcontainers로 실제 MySQL/Redis/Kafka 실행 — **모킹 금지**
- 테스트 픽스처: Instancio 사용
- 병렬 실행 비활성화 (`maxParallelForks = 1`)

---

## 기술 스택 참고

| 분류 | 라이브러리 | 버전 |
|---|---|---|
| Framework | Spring Boot | 3.4.4 |
| ORM | Spring Data JPA + QueryDSL | Spring Boot 관리 |
| API 문서 | SpringDoc OpenAPI | 2.7.0 |
| Test | Instancio JUnit | 5.0.2 |
| Test | Testcontainers | Spring Boot 관리 |
| Monitoring | Micrometer + Prometheus + Grafana | Spring Boot 관리 |
