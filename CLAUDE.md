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
interfaces.api          Controller, DTO, ApiSpec, ArgumentResolver
    ↓
application             Facade, Info, Command  (1:1 위임 + DTO 변환)
    ↓
domain                  Aggregate(Entity), VO, 기능별 Service, 도메인 컴포넌트,
                        도메인 정책(static), Repository(인터페이스)
    ↓
infrastructure          JpaRepository, RepositoryImpl 등
```

- `domain` 패키지는 인프라에 의존하지 않는다. Repository 인터페이스는 도메인에, 구현체는 infrastructure에 위치한다.
- Controller는 Facade만 호출한다. Domain Service를 직접 호출하지 않는다.
- DTO(`*V1Dto`)는 interfaces 레이어에만 존재한다. Domain → Application 사이는 Info 객체로 전달한다.

### 도메인 빌딩 블록

`domain/<bounded-context>/` 안에는 서로 다른 종류의 객체가 공존한다. 폴더 위치가 같다고 *역할이 같지 않다*.

| 종류 | 예시 | 특징 |
|---|---|---|
| **Aggregate Root / Entity** | `UserModel` | 식별자(id)가 있고 상태가 변한다. 불변식은 자기 메서드 안에서 강제. |
| **Value Object (VO)** | `LoginId`, `UserName`, `Email`, `RawPassword` | 식별자 없음, 불변, 속성이 곧 정체성. 생성자 = 형식 검증. |
| **Domain Policy** | `PasswordPolicy` (`static validate(...)`) | 여러 도메인 개념이 협력하는 *규칙*. 무상태. |
| **Domain Component** | `PasswordEncryptor` | 도메인 부품(인프라 래퍼 등). 이름에 `Service`를 붙이지 않는다. **Service가 자유롭게 의존 가능**. |
| **Service (기능별)** | `UserSignupService`, `UserAuthService`, `UserPasswordService` | 유스케이스 단위 도메인 흐름·검증·예외. Repository와 Domain Component에 의존. |
| **Repository (인터페이스)** | `UserRepository` | 도메인이 소유. 구현은 infrastructure. |

### Facade · Service · Component 책임 분담

> 같은 도메인의 혼선을 줄이기 위한 *프로젝트 규약*. 반복 결정하지 말고 이 표를 따른다.

| 레이어 | 책임 | 금지 |
|---|---|---|
| **Facade** (application) | ① 각 Service에 **1:1 위임** ② 도메인 모델 → DTO(`UserInfo`) 변환 | 흐름 분기·검증·예외 throw·트랜잭션 어노테이션 ❌ |
| **Service** (domain, 기능별) | 자기 유스케이스의 흐름·검증·예외, 트랜잭션 경계 | **다른 Service 의존 ❌** (Service ↔ Service 금지) |
| **Domain Component** (domain) | 재사용 가능한 도메인 부품 (인프라 래퍼·해시·암호화 등) | 비즈니스 흐름 ❌ |

**Service 분할 원칙**: 유스케이스(기능) 단위로 잘게 쪼갠다. 한 Service가 *너무 많은 책임을 들고* 다른 Service를 호출하고 싶어지면, 거기서 분할 시그널을 읽는다.

```
✅ UserSignupService    → signup
✅ UserAuthService      → authenticate, getById
✅ UserPasswordService  → changePassword

❌ UserService(범용)    → signup/authenticate/getById/changePassword 다 처리 (단일 책임 약함)
❌ UserSignupService    → UserPasswordService 의존 (Service ↔ Service)
```

**Facade의 메서드는 한 줄~서너 줄이 정상**. 흐름 분기(if/else)가 들어가기 시작하면 *해당 흐름을 Service 안으로* 옮긴다.

### 트랜잭션 규약

- **위치**: Facade가 아니라 **Service의 메서드 레벨**에 `@Transactional`을 명시한다.
  - 클래스 레벨은 readOnly/write 혼재 시 사고 위험 — 메서드 레벨이 의도가 코드에서 바로 보인다.
- **읽기 전용 메서드**: `@Transactional(readOnly = true)`.
- **Facade에 트랜잭션을 두지 않는 이유**: 외부 API 호출/비동기/단순 조회 조합 등 *트랜잭션 밖에서 처리해야 할 작업*이 합류할 자리를 비워둔다. *"같이 죽고 같이 사는 비즈니스 요구사항인가?"*에 Yes일 때만 더 큰 트랜잭션을 고려한다.
- **`@TransactionalEventListener(phase = AFTER_COMMIT)`**: 커밋 후 부수 효과(알림, 카프카 발행) 발행에 사용.

### 식별자 전달 규약

- Facade·Service 사이는 **`Long userId`(또는 식별자 단위)**로 전달한다.
- `ArgumentResolver`는 인증 후 식별자만 주입(`AuthUser`). Entity를 통째로 들고 다니지 않는다.
- 이유: ArgumentResolver의 조회 트랜잭션과 Service 트랜잭션이 분리돼 *detached entity* 처리(merge/save 재호출 등)가 필요해지는 부작용을 막는다. Service 안에서 한 번 더 조회하는 비용은 1차 캐시/인덱스 조회 수준으로 미미하다.

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

### 커밋 컨벤션

**작업 단위마다 커밋한다.** 구현과 테스트를 한 번에 커밋하지 않는다.

예시 흐름:
```
feat: 유저 도메인 설계        ← UserModel, UserService, UserRepository
test: 유저 도메인 단위 테스트  ← UserModelTest
feat: 유저 회원가입 API 구현   ← Controller, Facade, RepositoryImpl
test: 유저 회원가입 E2E 테스트 ← UserApiE2ETest
```

커밋 메시지 prefix:
- `feat` : 기능 구현
- `test` : 테스트 추가
- `fix`  : 버그 수정
- `docs` : 문서 변경
- `refactor` : 리팩토링 (기능 변경 없음)

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
