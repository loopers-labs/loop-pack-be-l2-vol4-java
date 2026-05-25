# CLAUDE.md

## 도메인 & 객체 설계 전략
- 도메인 객체는 비즈니스 규칙을 캡슐화해야 합니다.
- 애플리케이션 서비스는 서로 다른 도메인을 조립해, 도메인 로직을 조정하여 기능을 제공해야 합니다.
- 규칙이 여러 서비스에 나타나면 도메인 객체에 속할 가능성이 높습니다.
- 각 기능에 대한 책임과 결합도에 대해 개발자의 의도를 확인하고 개발을 진행합니다.

## 아키텍처, 패키지 구성 전략
- 본 프로젝트는 레이어드 아키텍처를 따르며, DIP (의존성 역전 원칙) 을 준수합니다.
- API request, response DTO와 응용 레이어의 DTO는 분리해 작성하도록 합니다.
- 패키징 전략은 4개 레이어 패키지를 두고, 하위에 도메인 별로 패키징하는 형태로 작성합니다.
  - 예시
    > /interfaces/api (presentation 레이어 - API)
      /application/.. (application 레이어 - 도메인 레이어를 조합해 사용 가능한 기능을 제공)
      /domain/.. (domain 레이어 - 도메인 객체 및 엔티티, Repository 인터페이스가 위치)
      /infrastructure/.. (infrastructure 레이어 - JPA, Redis 등을 활용해 Repository 구현체를 제공)



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
