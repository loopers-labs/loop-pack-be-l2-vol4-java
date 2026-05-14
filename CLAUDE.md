# CLAUDE.md

## 프로젝트 개요

이커머스 도메인 백엔드 멀티모듈 프로젝트 (Loopers Java Spring Template).  
`apps/`, `modules/`, `supports/` 세 레이어로 나뉜 Gradle 멀티모듈 구조를 사용한다.

---

## 기술 스택 및 버전

| 구분 | 기술 | 버전 |
|------|------|------|
| Language | Java | 21 |
| Framework | Spring Boot | 3.4.4 |
| Build | Gradle (Kotlin DSL) | 8.13 |
| Build Script | Kotlin | 2.0.20 |
| Spring Cloud | Spring Cloud Dependencies | 2024.0.1 |
| ORM | Spring Data JPA + QueryDSL (jakarta) | Boot managed |
| DB | MySQL | 8.0 |
| Cache | Redis (master-replica) | 7.0 |
| Messaging | Kafka (KRaft mode, bitnami) | 3.5.1 |
| API Docs | SpringDoc OpenAPI | 2.7.0 |
| Batch | Spring Batch | Boot managed |
| Monitoring | Micrometer + Prometheus + Grafana | Boot managed |
| Tracing | Micrometer Tracing (Brave) | Boot managed |
| Logging | Logback + Slack Appender | 1.6.1 |
| Boilerplate | Lombok | Boot managed |
| Test | JUnit 5 + AssertJ + Testcontainers | Boot managed |
| Test Mock | SpringMockk | 4.0.2 |
| Test Mock | Mockito | 5.14.0 |
| Test Fixture | Instancio JUnit | 5.0.2 |

---

## 모듈 구조

```
loopers-java-spring-template/
├── apps/                        # 실행 가능한 Spring Boot 애플리케이션
│   ├── commerce-api/            # REST API 서버 (Web MVC, Swagger)
│   ├── commerce-batch/          # Spring Batch 처리
│   └── commerce-streamer/       # Kafka 소비·생산 스트리밍 서버
│
├── modules/                     # 인프라 연동 공통 모듈 (java-library)
│   ├── jpa/                     # DataSource, JPA, QueryDSL 설정 + BaseEntity
│   ├── redis/                   # Redis 설정 (master/readonly 분리)
│   └── kafka/                   # Kafka 설정
│
├── supports/                    # 기술 지원 모듈 (java-library)
│   ├── jackson/                 # Jackson ObjectMapper 커스텀 설정
│   ├── logging/                 # Logback 설정, Slack Appender (dev/qa/prd)
│   └── monitoring/              # Actuator + Prometheus 설정
│
├── docker/
│   ├── infra-compose.yml        # MySQL, Redis(master+replica), Kafka, Kafka UI
│   └── monitoring-compose.yml   # Prometheus, Grafana
│
└── http/                        # IntelliJ HTTP Client 테스트 파일
```

### 모듈 의존 관계

```
commerce-api      → modules:jpa, modules:redis
                  → supports:jackson, supports:logging, supports:monitoring

commerce-batch    → modules:jpa, modules:redis
                  → supports:jackson, supports:logging, supports:monitoring

commerce-streamer → modules:jpa, modules:redis, modules:kafka
                  → supports:jackson, supports:logging, supports:monitoring
```

---

## 애플리케이션 레이어 구조 (commerce-api 기준)

```
interfaces/api/          # Controller, Dto, ApiSpec (OpenAPI 인터페이스)
application/             # Facade (유스케이스 조합), Info (응답 객체)
domain/                  # Model (JPA Entity + 비즈니스 로직), Service, Repository 인터페이스
infrastructure/          # JpaRepository 구현체, RepositoryImpl
support/error/           # CoreException, ErrorType
```

- **Model**: JPA `@Entity`. 생성자에서 도메인 유효성 검증 (`CoreException` throw).
- **Repository**: `domain` 패키지의 인터페이스 → `infrastructure` 패키지에서 구현.
- **Facade**: 여러 Service를 조합하는 애플리케이션 레이어. Controller는 Facade만 호출.
- **ApiSpec**: OpenAPI 어노테이션을 분리한 인터페이스. Controller가 구현.

---

## 테스트 전략

세 가지 테스트 레벨을 명확히 구분한다.

| 레벨 | 대상 | 애노테이션 | 특징 |
|------|------|-----------|------|
| 단위 테스트 | `domain/**/*ModelTest` | 없음 (순수 JUnit) | Spring 컨텍스트 없음 |
| 단위 테스트 | `domain/**/*ServiceUnitTest` | 없음 (순수 JUnit) | InMemoryRepository 사용 |
| 통합 테스트 | `domain/**/*ServiceIntegrationTest` | `@SpringBootTest` | Testcontainers + 실제 DB |
| E2E 테스트 | `interfaces/api/**/*ApiE2ETest` | `@SpringBootTest(RANDOM_PORT)` | `TestRestTemplate` 사용 |

- 테스트 메서드명: 영문 camelCase (`returnsExampleInfo_whenValidIdIsProvided`)
- `@DisplayName`: 한국어 서술
- `@Nested` 클래스로 시나리오 그룹화
- `@AfterEach`에서 `DatabaseCleanUp.truncateAllTables()` 호출
- 테스트 프로파일: `spring.profiles.active=test`, 타임존: `Asia/Seoul`

---

## 로컬 개발 환경 실행

### 인프라 구동

```bash
# MySQL, Redis(master+replica), Kafka, Kafka UI 실행
docker compose -f docker/infra-compose.yml up -d

# Prometheus, Grafana 실행
docker compose -f docker/monitoring-compose.yml up -d
```

### 포트 정보

| 서비스 | 포트 |
|--------|------|
| MySQL | 3306 |
| Redis master | 6379 |
| Redis readonly | 6380 |
| Kafka broker | 9092 / 19092 (host) |
| Kafka UI | 9099 |
| Prometheus | 9090 |
| Grafana | 3000 (admin/admin) |

### 빌드 및 실행

```bash
# commerce-api 실행
./gradlew :apps:commerce-api:bootRun

# commerce-batch 실행
./gradlew :apps:commerce-batch:bootRun

# commerce-streamer 실행
./gradlew :apps:commerce-streamer:bootRun

# 전체 테스트
./gradlew test

# JaCoCo 커버리지 리포트 생성
./gradlew jacocoTestReport
```

---

## 주요 설정

- `gradle.properties`: 모든 라이브러리 버전을 중앙 관리
- 각 `modules/*/src/main/resources/*.yml` 파일이 `application.yml`에 `spring.config.import`로 포함됨
- Redis는 master(쓰기)/readonly(읽기) 분리 구성
- Kafka는 KRaft 모드(ZooKeeper 없음), 단일 브로커 로컬 구성
- `springdoc.api-docs.enabled=false` (prd 프로파일에서 Swagger 비활성화)
- JaCoCo XML 리포트만 활성화 (CI 연동용)

---

## 주의사항

### 1. Never Do

- 실제 동작하지 않는 코드, 불필요한 Mock 데이터를 이용한 구현을 하지 말 것
- null-safety 하지 않게 코드 작성하지 말 것 (Java 의 경우, Optional 을 활용할 것)
- println 코드 남기지 말 것

### 2. Recommendation

- 실제 API 를 호출해 확인하는 E2E 테스트 코드 작성
- 재사용 가능한 객체 설계
- 성능 최적화에 대한 대안 및 제안
- 개발 완료된 API 의 경우, `http/**.http` 에 분류해 작성

### 3. Priority

1. 실제 동작하는 해결책만 고려
2. null-safety, thread-safety 고려
3. 테스트 가능한 구조로 설계
4. 기존 코드 패턴 분석 후 일관성 유지

---

## 개발 규칙

### 진행 Workflow - 증강 코딩

- **대원칙**: 방향성 및 주요 의사 결정은 개발자에게 제안만 할 수 있으며, 최종 승인된 사항을 기반으로 작업을 수행.
- **중간 결과 보고**: AI 가 반복적인 동작을 하거나, 요청하지 않은 기능을 구현, 테스트 삭제를 임의로 진행할 경우 개발자가 개입.
- **설계 주도권 유지**: AI 가 임의판단을 하지 않고, 방향성에 대한 제안 등을 진행할 수 있으나 개발자의 승인을 받은 후 수행.

### 개발 Workflow - TDD (Red > Green > Refactor)

- 모든 테스트는 3A 원칙으로 작성할 것 (Arrange - Act - Assert)

#### 1. Red Phase: 실패하는 테스트 먼저 작성

- 요구사항을 만족하는 기능 테스트 케이스 작성

#### 2. Green Phase: 테스트를 통과하는 코드 작성

- Red Phase 의 테스트가 모두 통과할 수 있는 코드 작성
- 오버엔지니어링 금지

#### 3. Refactor Phase: 불필요한 코드 제거 및 품질 개선

- 불필요한 private 함수 지양, 객체지향적 코드 작성
- unused import 제거
- 성능 최적화
- 모든 테스트 케이스가 통과해야 함

---

## CI / PR

- GitHub Actions: PR 생성·댓글 시 `qodo-ai/pr-agent` 자동 실행 (AI 코드 리뷰)
- PR 템플릿: `.github/pull_request_template.md`
- 자동 리뷰어 할당: `.github/auto_assign.yml`
