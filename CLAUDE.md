# loopers-java-spring-template

## 프로젝트 개요

Loop Pack BE L2 Vol4 — 커머스 도메인 백엔드 템플릿.
Spring Boot 기반 멀티 모듈 프로젝트로, REST API / Batch / Kafka Consumer 세 가지 실행 단위를 포함한다.

---

## 기술 스택 및 버전

| 항목 | 값 |
|---|---|
| Java | 21 |
| Spring Boot | 3.4.4 |
| Spring Cloud | 2024.0.1 |
| Gradle | 8.13 (Kotlin DSL) |
| Kotlin (Gradle 스크립트) | 2.0.20 |
| MySQL | 8.0 |
| Redis | 7.0 (master + readonly replica) |
| Kafka | 3.5.1 (KRaft 단일 브로커) |

### 주요 라이브러리

| 분류 | 라이브러리 | 버전 |
|---|---|---|
| ORM | Spring Data JPA + QueryDSL JPA (Jakarta) | Boot 관리 |
| Cache | Spring Data Redis | Boot 관리 |
| Messaging | Spring Kafka | Boot 관리 |
| Batch | Spring Batch | Boot 관리 |
| API Docs | Springdoc OpenAPI (WebMVC UI) | 2.7.0 |
| Observability | Micrometer + Prometheus + Brave | Boot 관리 |
| Serialization | Jackson (JSR310 + Kotlin module) | Boot 관리 |
| Logging | Logback + Slack Appender | 1.6.1 |
| Utilities | Lombok | Boot 관리 |
| Test | JUnit 5, Mockito 5.14.0, SpringMockk 4.0.2 | — |
| Test 데이터 | Instancio JUnit | 5.0.2 |
| Test 인프라 | Testcontainers (MySQL / Redis / Kafka) | Boot 관리 |

---

## 모듈 구조

```
loopers-java-spring-template/
├── apps/                          # 실행 가능한 애플리케이션 (BootJar)
│   ├── commerce-api               # REST API 서버
│   ├── commerce-batch             # Spring Batch 서버
│   └── commerce-streamer          # Kafka Consumer 서버
├── modules/                       # 인프라 연동 모듈 (java-library)
│   ├── jpa                        # JPA + QueryDSL + MySQL HikariCP 설정
│   ├── redis                      # Spring Data Redis 설정
│   └── kafka                      # Spring Kafka 설정
└── supports/                      # 횡단 관심사 모듈 (java-library)
    ├── jackson                    # ObjectMapper 설정
    ├── logging                    # Logback + Slack Appender
    └── monitoring                 # Actuator + Prometheus
```

### 모듈 의존 관계

```
commerce-api      → modules/jpa, modules/redis, supports/*
commerce-batch    → modules/jpa, modules/redis, supports/*
commerce-streamer → modules/jpa, modules/redis, modules/kafka, supports/*
```

---

## 레이어드 아키텍처 (commerce-api 기준)

```
interfaces.api          Controller, DTO, ApiSpec (OpenAPI 어노테이션 분리)
    ↓
application             Facade, Info (유스케이스 조합)
    ↓
domain                  Model (Entity), Service, Repository (인터페이스)
    ↓
infrastructure          JpaRepository, RepositoryImpl (도메인 인터페이스 구현)
```

- `domain` 패키지는 인프라에 의존하지 않는다. Repository 인터페이스는 도메인에, 구현체는 infrastructure에 위치한다.
- Controller는 Facade를 호출하며 Domain Service를 직접 호출하지 않는다.
- DTO(`*V1Dto`)는 interfaces 레이어에만 존재한다. Domain → Application 사이는 Info 객체로 전달한다.

### 공통 패턴

| 패턴 | 위치 |
|---|---|
| `BaseEntity` | `modules/jpa` — id(IDENTITY), createdAt, updatedAt, deletedAt, soft delete |
| `CoreException` | `apps/commerce-api/support/error` — ErrorType enum 기반 |
| `ApiResponse<T>` | `apps/commerce-api/interfaces/api` — 공통 응답 래퍼 |
| `ErrorType` | `INTERNAL_ERROR / BAD_REQUEST / NOT_FOUND / CONFLICT` |

---

## 테스트 전략

| 종류 | 어노테이션 | 특징 |
|---|---|---|
| 단위 테스트 | (순수 Java) | `@Nested` + `@DisplayName` |
| 통합 테스트 | `@SpringBootTest` | Testcontainers (MySQL/Redis/Kafka) |
| E2E 테스트 | `@SpringBootTest(RANDOM_PORT)` | TestRestTemplate 사용 |

- `@AfterEach`에서 `DatabaseCleanUp.truncateAllTables()` / `RedisCleanUp`으로 상태 초기화.
- 테스트 내부 주석 컨벤션: `// arrange / // act / // assert`.

---

## 인프라 (Docker Compose)

| 파일 | 내용 |
|---|---|
| `docker/infra-compose.yml` | MySQL 3306, Redis master 6379, Redis readonly 6380, Kafka 9092/19092, Kafka UI 9099 |
| `docker/monitoring-compose.yml` | Prometheus 9090, Grafana 3000 |

---

## 실행 프로파일

| 프로파일 | 용도 |
|---|---|
| `local` | 로컬 개발 (DDL auto: create, show-sql: true) |
| `test` | Testcontainers 기반 자동 테스트 |
| `dev` | 개발 서버 |
| `qa` | QA 서버 |
| `prd` | 운영 (Swagger UI 비활성화) |

기본 활성 프로파일: `local`

---

## CI/CD

- GitHub Actions (`main.yml`): PR 오픈/재오픈/ready_for_review 시 Qodo AI PR Agent 실행
- JaCoCo: XML 리포트 생성 (서브프로젝트 전체 적용)
- 병렬 테스트: `maxParallelForks = 1` (직렬 실행)

---

## 개발 시 주의사항

- **모듈 경계 엄수**: apps ↔ modules ↔ supports 간 역방향 의존 금지.
- **BootJar 설정**: `apps/` 하위만 BootJar 활성화. `modules/`, `supports/`는 일반 Jar.
- **QueryDSL APT**: `annotationProcessor` 설정이 각 모듈/앱마다 별도로 선언되어 있어야 Q클래스 생성된다.
- **타임존**: 애플리케이션 타임존 `Asia/Seoul`, DB 저장은 `NORMALIZE_UTC` (UTC 정규화).
- **soft delete**: `BaseEntity.delete()` / `restore()` 사용. 멱등 보장.

---

## 개발 규칙

### 진행 Workflow — 증강 코딩

- **대원칙**: 방향성 및 주요 의사결정은 개발자에게 제안만 할 수 있으며, 최종 승인된 사항을 기반으로 작업 수행.
- **중간 결과 보고**: AI가 반복적인 동작을 하거나, 요청하지 않은 기능을 구현하거나, 테스트를 임의로 삭제할 경우 개발자가 개입.
- **설계 주도권 유지**: AI가 임의 판단하지 않고, 방향성에 대한 제안을 진행할 수 있으나 개발자의 승인을 받은 후 수행.

### 개발 Workflow

1. 구현 코드를 먼저 작성한다.
2. 구현이 완료되면 테스트를 작성해 커버리지 100%를 목표로 한다.
3. 오버엔지니어링 금지. 불필요한 private 함수 지양, 객체지향적 코드 작성.
4. unused import 제거.
5. 모든 테스트 케이스가 통과해야 머지 가능하다.

### 테스트 전략

| 종류 | 인프라 | 목적 |
|---|---|---|
| 단위 테스트 | 테스트 더블만 사용 (Mock/Stub/Spy — 실제 DB/Redis/Kafka 사용 금지) | 단일 클래스/메서드 동작 검증 |
| 통합 테스트 | H2 인메모리 데이터베이스 | 레이어 간 연동 및 영속성 검증 |
| E2E 테스트 | 실제 HTTP 요청 (`TestRestTemplate`) | API 전체 흐름 검증 |

### 테스트 코드 컨벤션

**구조 — Given / When / Then**

```java
@Test
void 테스트메서드명() {
    // given

    // when

    // then
}
```

**메서드명**: camelCase로 작성한다.

**`@DisplayName`**: 항상 한국어로 작성하며, **"[상황] 시 [행동]하면 [결과]이다"** 삼박자를 갖춰야 한다.

```java
// 좋은 예
@DisplayName("회원가입 시도 시 이메일에 특수문자가 포함되지 않으면 IllegalArgumentException이 발생한다")

// 나쁜 예 — 결과가 빠짐
@DisplayName("이메일 형식 검증")
```

**`@Nested`**: 기능 단위로 중첩 클래스를 구성하고, `@DisplayName`으로 한국어 설명을 붙인다.

```java
@Nested
@DisplayName("회원가입 시")
class SignUp {

    @Test
    @DisplayName("유효한 이메일과 비밀번호를 입력하면 회원이 정상 생성된다")
    void createMember_whenValidEmailAndPasswordGiven() {
        // given
        // when
        // then
    }
}
```

### Never Do

- 실제 동작하지 않는 코드, 불필요한 Mock 데이터를 이용한 구현 금지.
- null-safety하지 않게 코드 작성 금지 (Java의 경우 `Optional` 활용).
- `println` 코드 남기지 말 것.

### Recommendation

- 실제 API를 호출해 확인하는 E2E 테스트 코드 작성.
- 재사용 가능한 객체 설계.
- 성능 최적화에 대한 대안 및 제안.
- 개발 완료된 API는 `.http/**.http`에 분류해 작성.

### Priority

1. 실제 동작하는 해결책만 고려.
2. null-safety, thread-safety 고려.
3. 테스트 가능한 구조로 설계.
4. 기존 코드 패턴 분석 후 일관성 유지.
