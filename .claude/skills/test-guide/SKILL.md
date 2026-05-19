---
name: test-guide
description: 테스트 케이스를 설계하고 작성할 때 이 프로젝트의 규칙과 설계 기법을 적용합니다.
---

테스트를 작성할 때 반드시 아래 흐름을 따른다.

## 1단계 — 테스트 계층을 먼저 결정한다

테스트를 바로 작성하기 전에, 지금 작성하려는 테스트가 어느 계층인지 먼저 명시한다.

| 계층 | 파일 패턴 | 검증 대상 |
|---|---|---|
| 단위 (Unit) | `domain/*ModelTest` | 도메인 규칙, 생성자 검증, 상태 변경 |
| 통합 (Integration) | `domain/*ServiceIntegrationTest` | Service + Repository + 실제 DB |
| E2E | `interfaces/api/*E2ETest` | HTTP 요청 전체 흐름 |

계층이 결정되면, 그 계층에 맞는 환경 설정을 사용한다:
- 단위: 순수 JUnit, Spring 컨텍스트 없음, 테스트 더블 사용
- 통합: `@SpringBootTest`, Testcontainers MySQL, `@AfterEach` DatabaseCleanUp
- E2E: `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `TestRestTemplate`

## 2단계 — 테스트 케이스 설계 기법으로 케이스를 도출한다

테스트 메서드를 바로 작성하지 않는다. 먼저 아래 기법들을 적용해 **어떤 케이스를 테스트할지** 목록을 만든다.

### 경계값 분석 (BVA)
숫자/길이/날짜 제약이 있는 필드는 반드시 경계를 테스트한다.
- 최솟값, 최솟값 -1, 최댓값, 최댓값 +1
- 예: 비밀번호 8~20자 → 7자(실패), 8자(성공), 20자(성공), 21자(실패)

### 동등 클래스 분할 (ECP)
같은 결과를 내는 입력끼리 묶고, 그룹당 대표값 하나만 테스트한다.
- 유효 클래스 1개 + 무효 클래스 N개 도출
- 예: 로그인 ID → 유효(`"user1"`), 형식 위반(`"user_1"`), 길이 초과(`"verylongid123"`)

### 결정 테이블
여러 조건이 조합되어 결과가 달라지는 경우 테이블로 정리한다.
- 모든 조건 조합을 나열하고 누락 케이스를 먼저 찾는다.

### 상태 전이 테스트
이벤트 후 상태가 바뀌는 케이스는 전이 전/후를 모두 검증한다.
- 예: 비밀번호 변경 → 구 비밀번호 401 확인 + 신 비밀번호 200 확인

### 오류 추측 (Error Guessing)
경험 기반으로 자주 발생하는 결함 패턴을 추가한다.
- null, 빈 문자열, 공백만 있는 입력
- 존재하지 않는 ID, 이미 삭제된 리소스
- 중복 요청 (동시 가입 등)

## 3단계 — AAA 패턴으로 테스트를 작성한다

모든 테스트는 반드시 세 블록으로 구분해서 작성한다.

```java
// arrange — 테스트에 필요한 입력/상태를 만든다
// act     — 검증하려는 메서드를 호출한다 (보통 한 줄)
// assert  — 결과 또는 상태 변화를 확인한다
```

여러 필드를 동시에 검증할 때는 `assertAll`을 사용한다.

## 4단계 — 테스트 메서드 이름 규칙을 지킨다

```
<기대결과>_when<조건>
```

예시:
- `throwsBadRequest_whenEmailFormatIsInvalid`
- `returnsOk_whenValidRequest`
- `throwsConflict_whenLoginIdAlreadyExists`
- `throwsUnauthorized_whenPasswordIsWrong`

이름만 봐도 "어떤 입력에 어떤 결과가 나와야 하는지" 즉시 알 수 있어야 한다.

## 5단계 — 테스트 더블 선택 기준을 따른다

| 역할 | 언제 사용하나 | 방법 |
|---|---|---|
| **Stub** | 흐름 제어 — 특정 응답을 고정해야 할 때 | `when().thenReturn()` |
| **Mock** | 호출 자체가 검증 대상일 때 | `verify(...)` |
| **Spy** | 진짜 로직은 유지하면서 일부만 검증 | `@MockitoSpyBean` |
| **Fake** | 실제 구현이 느리거나 비결정적일 때 | 직접 구현 |

단위 테스트에서 협력자는 무조건 테스트 더블로 대체한다. 통합 테스트에서는 실제 빈을 사용하고 Spy만 제한적으로 허용한다.

## 주의사항

- 테스트 격리: 각 테스트는 독립적으로 실행되어야 한다. `@AfterEach`에서 `DatabaseCleanUp.truncateAllTables()` 를 반드시 호출한다.
- 오버스펙 금지: 테스트가 요구하지 않은 로직을 구현에 미리 넣지 않는다.
- Mock 남용 금지: E2E 테스트에서 Mock을 쓰면 통합 신뢰도가 깨진다. E2E는 실제 흐름 그대로 검증한다.
- `println` 금지: 테스트 코드에 `System.out.println`을 남기지 않는다.

---

테스트 케이스 목록을 먼저 제안하고, 개발자의 확인 후 코드를 작성한다.
