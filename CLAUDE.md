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

## 현재 과제 (.codeguide/loopers-1-week.md)

회원 도메인(`User`) 구현:
- **회원 가입**: ID(영문+숫자 10자 이내), 이메일(`xx@yy.zz`), 생년월일(`yyyy-MM-dd`) 형식 검증
- **내 정보 조회**: `X-USER-ID` 헤더 기반 조회
- **포인트 조회**: 보유 포인트 반환

각 기능에 대해 단위/통합/E2E 테스트 케이스 구현 필요.
