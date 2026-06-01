# CLAUDE.md


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
