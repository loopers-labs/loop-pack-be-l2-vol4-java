# CLAUDE.md

## Commands

### 인프라 실행 (테스트/로컬 실행 전 필수)
```shell
docker-compose -f ./docker/infra-compose.yml up
```

### 빌드 및 테스트
```shell
./gradlew build
./gradlew test

# 특정 모듈
./gradlew :apps:commerce-api:test
./gradlew :apps:commerce-batch:test

# 단일 클래스 / 메서드
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.example.ExampleModelTest"
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.example.ExampleModelTest.Create.createsExampleModel_whenNameAndDescriptionAreProvided"
```

### 애플리케이션 실행
```shell
./gradlew :apps:commerce-api:bootRun
```

---

## 아키텍처

### 멀티 모듈 구조
- `apps/` — 실행 가능한 SpringBootApplication. `commerce-api`(REST API), `commerce-batch`(배치), `commerce-streamer`(Kafka 컨슈머)
- `modules/` — 도메인에 독립적인 재사용 설정. `jpa`, `redis`, `kafka`
- `supports/` — 부가 기능 애드온. `jackson`, `monitoring`, `logging`

### `commerce-api` 레이어 구조

```
interfaces/api  →  Controller, Dto, ApiSpec (Swagger)
application     →  Facade, Info (레이어 간 데이터 전달 record)
domain          →  Service, Model (Entity), Repository (인터페이스)
infrastructure  →  JpaRepository, RepositoryImpl
```

- **Controller**는 Facade만 호출한다. Service를 직접 호출하지 않는다.
- **Facade**는 여러 Service를 조합하는 역할이다. 단일 Service 호출도 Facade를 거친다.
- **Service**는 도메인 로직을 담당하며 `@Transactional`을 선언한다.
- **Repository**는 도메인 레이어에 인터페이스로 정의되고, `infrastructure`에서 구현한다 (의존성 역전).
- **Model**은 JPA Entity이자 도메인 객체다. 생성자에서 유효성 검증을 수행하며 유효하지 않으면 `CoreException`을 던진다.
- **Controller**는 `implements XxxV1ApiSpec` 형태로 Swagger 어노테이션을 인터페이스에 분리한다. Controller 본문에는 `@Operation` 등을 작성하지 않는다.

### API 응답 형식
모든 응답은 `ApiResponse<T>` record를 사용한다:
```json
{ "meta": { "result": "SUCCESS", "errorCode": null, "message": null }, "data": { ... } }
```
에러 시 `ApiControllerAdvice`가 `CoreException`을 잡아 `ApiResponse.fail()`로 변환한다.

### 예외 처리
- 비즈니스 예외는 `CoreException(ErrorType, customMessage)` 사용
- `ErrorType` enum: `BAD_REQUEST`, `NOT_FOUND`, `CONFLICT`, `INTERNAL_ERROR` (각각 HTTP 상태코드 매핑됨)

### BaseEntity
모든 Entity는 `modules/jpa`의 `BaseEntity`를 상속한다. `id`(AUTO_INCREMENT), `createdAt`, `updatedAt`, `deletedAt`을 자동 관리한다. 소프트 딜리트는 `delete()` / `restore()` 메서드로 처리하며 둘 다 멱등하게 동작한다.

---

## 테스트 관행

### 테스트 종류

| 종류 | 위치 | 어노테이션 | 특징 |
|------|------|-----------|------|
| 단위 테스트 | `domain/XxxModelTest` | 없음 (순수 Java) | Model 생성·유효성 검증 |
| 통합 테스트 | `domain/XxxServiceIntegrationTest` | `@SpringBootTest` | 실제 DB(Testcontainers), `DatabaseCleanUp`으로 격리 |
| E2E 테스트 | `interfaces/api/XxxApiE2ETest` | `@SpringBootTest(webEnvironment=RANDOM_PORT)` | `TestRestTemplate` 사용, 실제 HTTP 요청 |

- 테스트 메서드 이름은 `동사_when조건` 패턴을 따른다 (예: `returnsExampleInfo_whenValidIdIsProvided`)
- 각 테스트는 `// arrange / act / assert` 주석으로 구분한다
- 통합·E2E 테스트는 `@AfterEach`에서 `databaseCleanUp.truncateAllTables()`를 호출해 DB를 초기화한다
- `@Nested` + `@DisplayName`으로 테스트를 그룹화한다

### TDD 워크플로 (Red → Green → Refactor)

1. **Red** — 요구사항을 만족하는 실패하는 테스트 먼저 작성
2. **Green** — 테스트가 통과할 수 있는 최소한의 코드 작성 (오버엔지니어링 금지)
3. **Refactor** — 불필요한 코드 제거, unused import 제거, 객체지향적 코드로 개선. 모든 테스트가 통과해야 함

### 테스트 코드 작성 전 목록 확인 규칙

테스트 코드를 작성하기 전에 반드시 아래 형식으로 테스트 목록을 먼저 제시하고 사용자 확인을 받은 후 작성한다:

```
[레이어] 테스트 대상
- [ ] 케이스 설명 → 기대 결과
- [ ] 케이스 설명 → 기대 결과
```

예시:
```
[단위] UserModel 마스킹
- [ ] 이름이 두 글자 이상이면 마지막 글자만 * 처리 → "홍길동" → "홍길*"
- [ ] 이름이 한 글자이면 * 하나만 반환 → "*"

[통합] UserService 내 정보 조회
- [ ] 존재하는 loginId로 조회 → 회원 정보 반환
- [ ] 존재하지 않는 loginId로 조회 → null 반환

[E2E] GET /api/v1/users/me
- [ ] 정상 조회 → 200, 유저 정보 반환
- [ ] 존재하지 않는 ID → 404
```

### 테스트 실패로 인한 코드 수정 시 주석 규칙

테스트 실패를 수정하거나 누락된 코드를 추가할 때는 해당 코드 바로 위에 한 줄 주석을 남긴다:

```java
// [fix] <실패 원인 한 줄 요약>
```

예시:
```java
// [fix] gender null 검증 누락으로 성별 없는 요청이 200을 반환하던 버그 수정
if (gender == null) {
    throw new CoreException(ErrorType.BAD_REQUEST, "성별은 비어있을 수 없습니다.");
}
```

---

## 현재 구현 과제 (.codeguide/loopers-1-week.md)

회원 가입, 내 정보 조회, 포인트 조회 기능을 구현해야 한다. 각 기능마다 단위/통합/E2E 테스트를 필수로 구현하고 통과시켜야 한다. `apps/commerce-api` 내에 `example` 패키지의 구조를 참고해 동일한 레이어 패턴으로 작성한다.
