# CLAUDE.md

이 문서는  Claude Code가 이 프로젝트를 이해할 때 참고할 프로젝트 컨텍스트입니다.

## 프로젝트 개요

- **프로젝트명**: loopers-java-spring-template
- **그룹**: com.loopers
- **빌드 도구**: Gradle (Kotlin DSL)
- **언어**: Java 21

`apps` (실행 가능한 SpringBoot 애플리케이션), `modules` (재사용 가능한 인프라 설정), `supports` (부가 기능 add-on) 의 멀티 모듈 구조입니다.

## 기술 스택

- **런타임**: Java 21 · Spring Boot 3.4.4 · Spring Cloud 2024.0.1
- **API/문서**: SpringDoc OpenAPI 2.7.0
- **데이터**: MySQL 8.0 · Redis 7.0 · Kafka 3.5.1 · Spring Data JPA + QueryDSL (Jakarta)
- **관측**: Micrometer (Prometheus) · Logback Slack Appender 1.6.1
- **테스트**: JUnit 5 · Testcontainers · SpringMockK 4.0.2 · Mockito 5.14.0 · Instancio 5.0.2

## 모듈 구조

`settings.gradle.kts` 의 include 목록을 기준으로 합니다.

```
loopers-java-spring-template/
├── apps/                       # SpringBootApplication (실행 가능, BootJar)
│   ├── commerce-api            # 메인 REST API (web + actuator + springdoc)
│   ├── commerce-batch          # Spring Batch 잡 러너 (web-application-type: none)
│   └── commerce-streamer       # Kafka 소비 + REST (web + kafka)
├── modules/                    # 재사용 가능한 인프라 설정 (java-library + test-fixtures)
│   ├── jpa                     # Spring Data JPA + QueryDSL + MySQL + Testcontainers fixtures
│   ├── redis                   # Spring Data Redis + Testcontainers fixtures
│   └── kafka                   # spring-kafka + Testcontainers fixtures
└── supports/                   # add-on 모듈
    ├── jackson                 # Jackson 설정
    ├── logging                 # Actuator + Micrometer + Slack 알림
    └── monitoring              # Actuator + Prometheus 메트릭
```

## commerce-api 아키텍처

**Clean Architecture** 4-레이어 구조. 도메인 단위 패키지를 각 레이어 하위에 둠 (`product/`).

핵심 원칙은 **의존성 규칙** — 소스 코드 의존성은 항상 안쪽 (도메인) 방향으로만 향한다. 바깥 레이어는 안쪽을 알지만, 안쪽은 바깥을 모른다.

```
com.loopers
├── interfaces/api    # Interface Adapters (Controllers)
│   ├── {도메인}/*V1Controller, *V1ApiSpec, *V1Dto
│   ├── ApiResponse<T>            # 공통 응답 래퍼
│   └── ApiControllerAdvice       # 전역 예외 핸들러
├── application       # Use Cases (Application Business Rules)
│   └── {도메인}/*Facade, *Info
├── domain            # Entities (Enterprise Business Rules)
│   └── {도메인}/*Model, *Service, *Repository(인터페이스)
├── infrastructure    # Frameworks & Drivers (Gateways · DB · 외부 시스템)
│   └── {도메인}/*RepositoryImpl, *JpaRepository
└── support           # 횡단 관심사
    └── error/CoreException, ErrorType
```

### 레이어별 책임

| 레이어 | Clean Architecture 대응 | 책임 | 허용 의존 |
|--------|------------------------|------|-----------|
| `interfaces` | Interface Adapters | HTTP 요청/응답 변환, 입력 검증, DTO 매핑 | → `application` |
| `application` | Use Cases | 유스케이스 조립, 트랜잭션 외곽, 도메인 결과를 `*Info` 로 변환 | → `domain` |
| `domain` | Entities | 비즈니스 규칙, 모델 불변식, Repository 인터페이스 정의 | (외부 의존 없음) |
| `infrastructure` | Frameworks & Drivers | JPA·외부 API 구현. 의존성 역전으로 `domain` 의 인터페이스 구현 | → `domain` |

> **의존성 역전 포인트**: `infrastructure/*RepositoryImpl` 이 `domain/*Repository` (인터페이스) 를 구현. 도메인은 JPA·MySQL 의 존재를 모름.

### 호출 흐름

```
Request
  → *V1Controller (interfaces)
    → *Facade (application)
      → *Service (domain)
        → *Repository (domain — 인터페이스)
          → *RepositoryImpl (infrastructure — 의존성 역전)
            → *JpaRepository (Spring Data)
  ← *Model
  ← *Info (from Model)
  ← *V1Dto.Response (from Info)
Response (ApiResponse<T>)
```

### 네이밍 / 코드 컨벤션

- **API 버저닝**: URL `/api/v1/{resource}`, 클래스 prefix `*V1*` (`ProductV1Controller`, `ProductV1Dto`).
- **컨트롤러 + 스펙 분리**: `*V1Controller implements *V1ApiSpec` 패턴. 스펙 인터페이스에 SpringDoc 어노테이션을 둠.
- **DTO 네스팅**: `*V1Dto` 클래스 내부에 `Request`/`Response` 정적 클래스로 묶음.
- **레이어 간 변환**: `Model` (domain) → `Info` (application) → `Dto.Response` (interfaces). `from(...)` 정적 팩토리 사용.
- **DI**: `@RequiredArgsConstructor` + `final` 필드 (Lombok).
- **스테레오타입**: `@Component` 를 일관 사용 (`@Service`/`@Repository` 미사용).
- **에러 처리**: 도메인은 `throw new CoreException(ErrorType.X, message)`, 전역 매핑은 `ApiControllerAdvice` 가 담당.
- **응답 포맷**: 모든 성공 응답은 `ApiResponse.success(payload)` 로 래핑.

### 새 도메인 추가 시 체크리스트

1. `domain/{도메인}/` 에 `Model`, `Repository` (interface), `Service` 생성.
2. `infrastructure/{도메인}/` 에 `JpaRepository` (Spring Data) + `RepositoryImpl` (도메인 인터페이스 구현) 추가.
3. `application/{도메인}/` 에 `Facade` + `Info` 추가.
4. `interfaces/api/{도메인}/` 에 `V1Controller` + `V1ApiSpec` + `V1Dto` 추가.
5. `CoreException` / `ErrorType` 에 도메인 에러 유형 등록.

## 빌드 / 실행

### Gradle 태스크 규약
- 루트, `apps/`, `modules/`, `supports/` 그룹 자체 프로젝트는 `tasks.configureEach { enabled = false }` 로 비활성화되어 있어 직접 실행되지 않습니다.
- `apps/*` 만 `BootJar` 가 활성화되고, `modules/*` 와 `supports/*` 는 일반 `Jar` 만 빌드됩니다.
- 빌드 버전: `version` 미지정 시 `git rev-parse --short HEAD` 값을 사용 (없으면 `init`).

### 자주 쓰는 명령

```shell
# 전체 빌드
./gradlew build

# 특정 앱만 실행
./gradlew :apps:commerce-api:bootRun
./gradlew :apps:commerce-batch:bootRun
./gradlew :apps:commerce-streamer:bootRun

# 테스트 & 커버리지
./gradlew test
./gradlew jacocoTestReport
```

> 로컬 인프라 (MySQL / Redis / Kafka / Prometheus·Grafana) 는 `docker/infra-compose.yml`, `docker/monitoring-compose.yml` 참고.

## 코드 스타일

- `.editorconfig` 기준: Java/Kotlin 파일 `max_line_length=130`, `*Test.java` 는 제한 없음, EOF 줄바꿈 강제.
- Lombok 사용 허용.

## 개발 규칙

### 증강 코딩 원칙

- **의사결정 주도권**: 방향성 및 주요 설계 결정은 개발자 승인 필수. AI 는 제안만.
- **중간 보고**: 반복 작업, 요청하지 않은 기능, 테스트 삭제·스킵·약화 시 사전 확인 필수.
- **임의 판단 금지**: 요구사항·도메인 규칙이 불확실하면 추측 금지, 반드시 확인 후 진행.
- **검증 가능성**: 작성·수정 후 빌드/테스트 통과 확인. 검증 불가 시 명시.

### Tdd Workflow (Red → Green → Refactor)

**모든 테스트는 GWT 원칙 준수**: `Given` → `When` → `Then` 블록으로 구분 작성.

1. **Red**: 요구사항을 표현하는 실패하는 테스트 먼저 작성. 
   - 의도가 드러나는 테스트 이름·시나리오 우선.

2. **Green**: 테스트를 통과시키는 **최소한의** 구현. 
   - 과한 일반화·선제 추상화 금지.
 
3. **Refactor**: 행위 변경 없이 구조 개선. 
   - 중복 제거·네이밍 정리
   - 불필요한 private 함수 지양하고 객체지향적 설계로 정리
   - unused import 제거, 필요 시 성능 최적화
   - **모든 테스트 통과 유지 필수**.

- 각 사이클은 작게 (한 번에 한 동작). 큰 변경이 필요하면 사이클을 쪼개서 진행.
- 실패 메시지로 다음 스텝을 결정. 실패 없이 코드 먼저 작성하지 않음.

## 주의사항

### 절대 금지 (Never)

- **동작하지 않는 코드 / 가짜 구현**: 컴파일만 되는 빈 껍데기, 의미 없는 Mock 데이터 제출.
- **`println` / `System.out.println`**: 디버깅용 포함, 어떤 경우에도 사용 금지.
- **Null 노출**: 반환 타입에 `null` 금지 — `Optional` 사용, 컬렉션은 빈 컬렉션 반환.
- **의도 없는 예외 catch**: 빈 catch block, 광범위한 `catch (Exception e)` 로 swallow 금지.
- **테스트 무력화**: `@Disabled`, `@Ignore`, 단언 제거, 의미 없는 assert 로 통과시키기 금지.

### 추천 (Recommendation)

- **재사용 가능한 객체 설계**: 단일 책임·명확한 협력 관계 우선. 특정 호출 경로에 종속된 설계 지양.
- **E2E 테스트 작성**: 실제 API 호출로 요청·응답·상태 변화를 검증.
- **API 문서화**: 개발 완료된 API 는 `http/**.http` 에 도메인·기능별로 분류해 작성.
- **로깅**: SLF4J (Lombok `@Slf4j`) 사용.

### 우선순위 (Priority)

여러 개선점이 충돌할 때의 판단 기준:

1. **동작하는 해결책** — 가짜·미완성 구현 대신 실제 동작하는 코드 우선.
2. **안전성** — null-safety, thread-safety, 예외 처리, 트랜잭션 경계.
3. **유지보수성** — 기존 코드 패턴 분석 후 컨벤션 일관성 유지, 테스트 가능한 구조로 설계.
4. **가독성** — 네이밍, 매직값 제거, 함수·클래스 책임 분리.

## 프로젝트 컨벤션

- 새 의존성 버전은 `gradle.properties` 에 키로 추가 후 `project.properties["..."]` 로 참조.
- 도메인 로직은 `apps/<app>` 의 `application` / `domain` / `infrastructure` / `interfaces` 계층 구분. `modules/*` 에 도메인 코드 금지.
- 테스트는 통합 테스트 기본, Testcontainers 활용 (`modules/{jpa,redis,kafka}` 의 `testFixtures`).
- 테스트 실행은 단일 포크 (`maxParallelForks = 1`) — 컨테이너 충돌 우려 없음.
- CI: PR 이벤트에 `.github/workflows/main.yml` 의 qodo-ai PR Agent 동작.
