# CLAUDE.md

## DDD 기반 구현

### 도메인 & 객체 설계 전략
- 도메인 객체는 비즈니스 규칙을 캡슐화해야 합니다.
- 애플리케이션 서비스는 서로 다른 도메인을 조립해, 도메인 로직을 조정하여 기능을 제공해야 합니다.
- 규칙이 여러 서비스에 나타나면 도메인 객체에 속할 가능성이 높습니다.
- 각 기능에 대한 책임과 결합도에 대해 개발자의 의도를 확인하고 개발을 진행합니다.
- Domain Service는 Repository 의존 없이 순수 도메인 로직만 담당합니다. 도메인 객체를 파라미터로 받아 비즈니스 규칙을 수행하고 도메인 객체를 반환합니다. 크로스 도메인 협력 로직도 DomainService가 담당하며, 이때 Facade가 로드한 타 도메인 객체를 파라미터로 받아 처리합니다.
- Application Layer(Facade)는 Repository 로드·저장과 DomainService 호출 순서 조율을 담당합니다. 크로스 도메인 비즈니스 로직을 Facade에 인라인으로 직접 작성하지 않습니다.

### 아키텍처, 패키지 구성 전략
- 본 프로젝트는 4티어 레이어드 아키텍처를 따릅니다: `interfaces → application → domain ← infrastructure`
- DIP(의존성 역전 원칙)는 교체 가능성이 있는 곳에만 적용합니다.
  - 적용: `Repository` 인터페이스, `PasswordEncryptor` 인터페이스
  - 미적용: `Facade`, `DomainService` 등 구현체를 교체할 시나리오가 없는 곳
- API request/response DTO와 응용 레이어의 DTO는 분리해 작성합니다.
- 패키징 전략은 **도메인 중심(Domain-first, 약하게)** 으로 구성합니다.
  - 최상위는 도메인 패키지, 그 안에 레이어 서브패키지를 둡니다.
  - 레이어 서브패키지를 유지함으로써 레이어 경계를 코드 구조로 표현합니다.

```
com.loopers/
├── support/                        ← 도메인 무관 공통 코드
│   ├── error/                      (CoreException, ErrorType)
│   ├── response/                   (ApiResponse, ApiControllerAdvice)
│   └── auth/                       (CurrentUser, LoginUser, LoginUserResolver)
│
├── {domain}/                       ← user, brand, product, like, order
│   ├── domain/                     (Model, Repository 인터페이스, Service)
│   ├── application/                (Facade, Info DTO)
│   ├── infrastructure/             (Repository 구현체, JpaRepository)
│   └── interfaces/                 (Controller, DTO — 고객/어드민 파일명으로 구분)
```

- 어드민/고객 API는 같은 `interfaces/` 안에서 파일명으로 구분합니다.
  - 고객: `BrandV1Controller.java`
  - 어드민: `AdminBrandV1Controller.java`
- `OrderItemModel`은 `order/domain/` 안에 위치합니다. (Order 없이 독립 존재 불가)



## 테스트 관행

### 테스트 종류

| 종류 | 위치 | 어노테이션 | 특징 |
|------|------|-----------|------|
| 단위 테스트 | `{domain}/domain/XxxModelTest` | 없음 (순수 Java) | Model 생성·유효성 검증 |
| 단위 테스트 | `{domain}/domain/XxxServiceTest` | 없음 (순수 Java) | DomainService 비즈니스 로직 검증 (Repository 불필요) |
| 통합 테스트 | `{domain}/application/XxxFacadeIntegrationTest` | `@SpringBootTest` | 실제 DB(Testcontainers), `DatabaseCleanUp`으로 격리 |
| E2E 테스트 | `{domain}/interfaces/XxxApiE2ETest` | `@SpringBootTest(webEnvironment=RANDOM_PORT)` | `TestRestTemplate` 사용, 실제 HTTP 요청 |

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

[단위] UserService 회원가입
- [ ] 이미 존재하는 loginId → CONFLICT 예외
- [ ] 존재하지 않는 loginId → 비밀번호 인코딩 후 UserModel 반환

[통합] UserFacade 회원가입
- [ ] 정상 요청 → DB에 저장되고 UserInfo 반환
- [ ] 중복 loginId → CONFLICT 예외

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
