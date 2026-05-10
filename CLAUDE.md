# CLAUDE.md

## 프로젝트 개요

**프로젝트명**: `loopers-java-spring-template`
**그룹**: `com.loopers`
**빌드 도구**: Gradle 8.13 (Kotlin DSL)

---

## 기술 스택 및 버전

| 분류 | 기술 | 버전 |
|------|------|------|
| Language | Java | 21 |
| Framework | Spring Boot | 3.4.4 |
| Spring Cloud | Spring Cloud Dependencies | 2024.0.1 |
| ORM | Spring Data JPA + Hibernate | Spring Boot 관리 |
| Query | QueryDSL JPA | Spring Boot 관리 |
| DB | MySQL | 8.0 |
| Cache | Redis | 7.0 (master-replica) |
| Messaging | Apache Kafka | 3.5.1 (KRaft 모드) |
| Batch | Spring Batch | Spring Boot 관리 |
| API Docs | Springdoc OpenAPI | 2.7.0 |
| Monitoring | Micrometer + Prometheus + Grafana | Spring Boot 관리 |
| Logging | Logback + Slack appender | 1.6.1 |
| Utility | Lombok, Jackson | Spring Boot 관리 |

### 테스트 라이브러리

| 분류 | 라이브러리 | 버전 |
|------|-----------|------|
| Test Framework | JUnit 5 | Spring Boot 관리 |
| Testcontainers | MySQL, Redis, Kafka | Spring Boot 관리 |
| Mocking | SpringMockk | 4.0.2 |
| Mocking | Mockito | 5.14.0 |
| Fixture | Instancio | 5.0.2 |
| Assertion | AssertJ | Spring Boot 관리 |

---

## 멀티 모듈 구조

```
loopers-java-spring-template/
├── apps/                       # 실행 가능한 애플리케이션 (BootJar 생성)
│   ├── commerce-api/           # REST API 서버 (Spring MVC, Swagger 포함)
│   ├── commerce-batch/         # 배치 처리 (Spring Batch)
│   └── commerce-streamer/      # Kafka 스트리밍 처리 (Consumer)
│
├── modules/                    # 인프라 공유 모듈 (java-library)
│   ├── jpa/                    # JPA + QueryDSL + MySQL + HikariCP
│   ├── redis/                  # Spring Data Redis (master-replica)
│   └── kafka/                  # Spring Kafka
│
├── supports/                   # 공통 지원 모듈 (java-library)
│   ├── jackson/                # Jackson ObjectMapper 설정
│   ├── logging/                # Logback 설정 (JSON, Slack 알림)
│   └── monitoring/             # Actuator + Prometheus
│
└── docker/
    ├── infra-compose.yml       # MySQL, Redis(master+replica), Kafka, Kafka-UI
    └── monitoring-compose.yml  # Prometheus, Grafana
```

### 모듈 의존 관계

- `apps/*` → `modules/jpa`, `modules/redis`, `supports/jackson`, `supports/logging`, `supports/monitoring`
- `apps/commerce-streamer` → 추가로 `modules/kafka`
- `modules/*`, `supports/*` → 단독 실행 불가 (java-library)

---

## DDD 기반 계층 구조 (apps/commerce-api)

```
src/main/java/com/loopers/
├── interfaces/                          # 표현 계층 (Presentation Layer)
│   └── api/
│       ├── ApiControllerAdvice.java     # 전역 예외 핸들러
│       ├── ApiResponse.java             # 공통 응답 래퍼 (record)
│       └── {domain}/
│           ├── {Domain}V1ApiSpec.java   # OpenAPI 스펙 인터페이스 (선택적)
│           ├── {Domain}V1Controller.java # REST 컨트롤러
│           └── {Domain}V1Dto.java        # HTTP Request/Response DTO (record)
│
├── application/                         # 응용 계층 (Application Layer)
│   └── {domain}/
│       ├── {Domain}Facade.java          # 유즈케이스 조합 (도메인 서비스 호출)
│       └── {Domain}Info.java            # 계층 간 데이터 전달 객체 (record)
│
├── domain/                              # 도메인 계층 (Domain Layer)
│   └── {domain}/
│       ├── {Domain}Model.java           # 도메인 엔티티 (@Entity, 비즈니스 로직 포함)
│       ├── {Domain}Repository.java      # 리포지토리 인터페이스 (도메인 정의)
│       └── {Domain}Service.java         # 도메인 서비스 (@Component, @Transactional)
│
├── infrastructure/                      # 인프라 계층 (Infrastructure Layer)
│   └── {domain}/
│       ├── {Domain}JpaRepository.java   # Spring Data JPA 인터페이스
│       └── {Domain}RepositoryImpl.java  # Repository 인터페이스 구현체
│
└── support/                             # 공통 지원
    └── error/
        ├── CoreException.java           # 도메인 예외 (ErrorType 포함)
        └── ErrorType.java               # 에러 유형 enum (HttpStatus 매핑)
```

### 계층별 역할 및 규칙

**interfaces (표현 계층)**
- `{Domain}V1Controller`: `@RestController`, Facade를 주입받아 호출
- `{Domain}V1Dto`: `record` 타입. Request/Response 분리. `ProductV1Dto.ProductResponse.from(info)` 패턴으로 Info를 변환
- `ApiResponse<T>`: 모든 응답은 `{ meta: { result, errorCode, message }, data }` 구조로 통일

**application (응용 계층)**
- `{Domain}Facade`: 트랜잭션 없이 도메인 서비스를 조합. `@Component`
- `{Domain}Info`: `record` 타입. Domain Model → Info 변환은 `Info.from(model)` 정적 팩토리 메서드 사용

**domain (도메인 계층)**
- `{Domain}Model`: `@Entity`, `BaseEntity` 상속. 생성자와 메서드 내부에서 유효성 검증 후 `CoreException` throw
- `{Domain}Repository`: 도메인 계층에 정의된 순수 인터페이스 (Spring Data 미의존)
- `{Domain}Service`: `@Component`, `@Transactional`. Repository 인터페이스만 의존

**infrastructure (인프라 계층)**
- `{Domain}JpaRepository`: `JpaRepository<Model, Long>` 상속
- `{Domain}RepositoryImpl`: 도메인 `Repository` 구현, `JpaRepository` 위임

### BaseEntity (modules/jpa)

```java
// modules/jpa/src/main/java/com/loopers/domain/BaseEntity.java
@MappedSuperclass
public abstract class BaseEntity {
    @Id @GeneratedValue(strategy = IDENTITY)
    private final Long id = 0L;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private ZonedDateTime deletedAt;  // soft delete 지원

    protected void guard() {}  // PrePersist/PreUpdate 시 유효성 검증 훅
    public void delete() { ... }   // 멱등 soft delete
    public void restore() { ... }  // 멱등 복원
}
```

---

## 테스트 전략 (3계층)

| 계층 | 클래스명 패턴 | 특징 |
|------|-------------|------|
| 단위 테스트 | `{Domain}ModelTest` | 순수 Java, Spring 컨텍스트 없음. 도메인 객체 생성/검증 로직 테스트 |
| 통합 테스트 | `{Domain}ServiceIntegrationTest` | `@SpringBootTest`, Testcontainers. 실제 DB 사용 |
| E2E 테스트 | `{Domain}V1ApiE2ETest` | `@SpringBootTest(webEnvironment=RANDOM_PORT)`, `TestRestTemplate`. 전체 HTTP 흐름 |

**공통 패턴:**
- `@AfterEach`에서 `databaseCleanUp.truncateAllTables()` 호출로 테스트 격리
- given-when-then 구조: `// arrange`, `// act`, `// assert` 주석 사용
- `@DisplayName` + `@Nested`로 계층적 테스트 구조화

---

## 환경 프로파일

| 프로파일 | 설명 |
|---------|------|
| `local` | 로컬 개발. DDL auto=create, show-sql=true |
| `test` | 테스트 (Testcontainers 사용). DDL auto=create |
| `dev` | 개발 서버 |
| `qa` | QA 서버 |
| `prd` | 운영. Swagger 비활성화, DDL auto=none |

---

## 인프라 설정

### MySQL (HikariCP)
- Main pool: max 40, min-idle 30
- `rewriteBatchedStatements=true` (배치 성능 최적화)
- UTC 기준 시간대 저장 (`timezone.default_storage: NORMALIZE_UTC`)

### Redis
- Master(6379) + Replica(6380) 구성
- AOF 영속성 활성화, replica-read-only=yes

### Kafka
- KRaft 모드 (ZooKeeper 없음)
- 내부 통신: `kafka:9092`, 호스트 접속: `localhost:19092`
- Kafka UI: `localhost:9099`

---

## 빌드 및 실행 명령어

```bash
# 전체 빌드
./gradlew build

# 특정 모듈 테스트
./gradlew :apps:commerce-api:test

# 인프라 실행 (MySQL, Redis, Kafka)
docker-compose -f docker/infra-compose.yml up -d

# 모니터링 실행 (Prometheus, Grafana)
docker-compose -f docker/monitoring-compose.yml up -d
```

---

## 개발 규칙
### 진행 Workflow - 증강 코딩
- **대원칙** : 방향성 및 주요 의사 결정은 개발자에게 제안만 할 수 있으며, 최종 승인된 사항을 기반으로 작업을 수행.
- **중간 결과 보고** : AI 가 반복적인 동작을 하거나, 요청하지 않은 기능을 구현, 테스트 삭제를 임의로 진행할 경우 개발자가 개입.
- **설계 주도권 유지** : AI 가 임의판단을 하지 않고, 방향성에 대한 제안 등을 진행할 수 있으나 개발자의 승인을 받은 후 수행.

### 개발 Workflow - TDD (Red > Green > Refactor)
- 모든 테스트는 3A 원칙으로 작성할 것 (Arrange - Act - Assert)
- 기존 작성되어 있는 코드 style 및 패턴을 분석하여 일관성을 유지한다.

#### 1. Red Phase : 실패하는 테스트 먼저 작성
- 요구사항을 만족하는 기능 테스트 케이스 작성
- 기존 테스트와의 충돌/중복을 사전에 확인한다.
- 각 테스트는 독립적으로 실행 가능해야 한다 (테스트 간 의존 금지).

#### 2. Green Phase : 테스트를 통과하는 코드 작성
- Red Phase 의 테스트가 모두 통과할 수 있는 코드 작성
- 최소 구현 원칙 (Minimum Viable Implementation) 적용 (오버 엔지니어링 금지)
- 새 코드가 기존 테스트를 깨뜨리지 않도록 한다.

#### 3. Refactor Phase : 불필요한 코드 제거 및 품질 개선
- 불필요한 private 함수 지양, 객체지향적 코드 작성
- unused import 제거
- 성능 최적화
- 모든 테스트 케이스가 통과해야 함

#### 4. Review Phase : 코드 리뷰 및 피드백 반영
- 작성된 코드에 대한 리뷰 진행
- 코드를 수정하지 않는다. 이슈와 개선안만 보고한다.
- 프로젝트의 실제 코드, 정책, 아키텍처를 근거로 판단한다.

## 주의사항
### 1. Never Do
- 실제 동작하지 않는 코드, 불필요한 Mock 데이터를 이요한 구현을 하지 말 것
- null-safety 하지 않게 코드 작성하지 말 것 (Java 의 경우, Optional 을 활용할 것)
- println 코드 남기지 말 것

### 2. Recommendation
- 실제 API 를 호출해 확인하는 E2E 테스트 코드 작성
- 재사용 가능한 객체 설계
- 성능 최적화에 대한 대안 및 제안
- 개발 완료된 API 의 경우, `.http/**.http` 에 분류해 작성

### 3. Priority
1. 실제 동작하는 해결책만 고려
2. null-safety, thread-safety 고려
3. 테스트 가능한 구조로 설계
4. 기존 코드 패턴 분석 후 일관성 유지

## 현재 과제(Volume-1)

회원 도메인(`User`) 구현:
### Users Table 정보
CREATE TABLE users
```
(
id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'PK',
login_id   VARCHAR(20)  NOT NULL COMMENT '로그인 ID (영문+숫자)',
password   VARCHAR(255) NOT NULL COMMENT '암호화된 비밀번호 (BCrypt)',
name       VARCHAR(50)  NOT NULL COMMENT '회원 이름',
birth_date DATE         NOT NULL COMMENT '생년월일 (yyyy-MM-dd)',
email      VARCHAR(255) NOT NULL COMMENT '이메일',
created_at DATETIME     NOT NULL COMMENT '생성 일시 (UTC)',
updated_at DATETIME     NOT NULL COMMENT '수정 일시 (UTC)',
deleted_at DATETIME              COMMENT '삭제 일시 (UTC) - Soft Delete',

      PRIMARY KEY (id),
      UNIQUE INDEX uq_users_login_id (login_id),
      INDEX idx_users_deleted_at (deleted_at)
)
```

### 기능 구현
#### 회원가입
**필요 정보 : { 로그인 ID, 비밀번호, 이름, 생년월일, 이메일 }**
이미 가입된 로그인 ID 로는 가입이 불가능함
각 정보는 포맷에 맞는 검증 필요 (이름, 이메일, 생년월일)
비밀번호는 암호화해 저장하며, 아래와 같은 규칙을 따름
1. 8~16자의 영문 대소문자, 숫자, 특수문자만 가능합니다.
2. 생년월일은 비밀번호 내에 포함될 수 없습니다.

이후, 유저 정보가 필요한 모든 요청은 아래 헤더를 통해 요청
* X-Loopers-LoginId : 로그인 ID
* X-Loopers-LoginPw : 비밀번호

#### 내 정보 조회
**반환 정보 : { 로그인 ID, 이름, 생년월일, 이메일 }**
- 로그인 ID 는 영문과 숫자만 허용
- 이름은 마지막 글자를 마스킹해 반환 (마스킹 문자는 * 로 통일)

#### 비밀번호 수정
  **필요 정보 : { 기존 비밀번호, 새 비밀번호 }**
- 비밀 번호 RULE 을 따르되, 현재 비밀번호는 사용할 수 없습니다.

비밀번호 RULE
* 8~16자의 영문 대소문자, 숫자, 특수문자만 가능합니다.
* 생년월일 사용 불가

### 각 기능에 대해 단위/통합/E2E 테스트 케이스 구현 필요.


