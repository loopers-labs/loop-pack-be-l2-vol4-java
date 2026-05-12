---
name: tdd-helper
description: loopers(Spring Boot 헥사고날) 프로젝트에서 TDD로 기능을 구현하거나 버그를 수정·리팩터링할 때 활성화된다. Red-Green-Refactor 사이클, Tidy First(구조 변경과 행위 변경 분리), plan.md + "go" 트리거 워크플로우, outside-in 레이어 순서(도메인 → 서비스 → 통합 → E2E)를 안내한다. 사용자가 "구현해줘", "만들어줘", "TDD로", "테스트 먼저", "기능 추가", "API 만들기", "회원가입/주문/상품 같은 도메인 작업", "버그 픽스", "결함 수정", "리팩터링" 같은 표현을 쓰거나, 새 도메인·유스케이스·컨트롤러·.http 파일을 작성할 때 사용한다. CoreException/BaseEntity/ApiResponse 등 프로젝트 컨벤션을 따르는 TDD 패턴과 Never Do 가드레일을 제공한다 (커밋 규율은 smart-commit 스킬이 담당).
---

# TDD Helper

이 스킬은 loopers(Spring Boot 헥사고날) 프로젝트에서 새 기능 구현·버그 픽스·리팩터링 작업의 워크플로우와 규율을 안내한다.

프로젝트 구조·기술 스택·명령어는 루트 `CLAUDE.md`를 참조한다. 이 스킬은 그 위에 얹히는 작업 절차다.

---

## 0. ⛔ 절대 우회 금지 — 검토 게이트 (READ FIRST)

이 스킬은 **AI와 사용자가 함께 TDD를 하는 협업 모델**이다. 코드 작성은 AI가, **검토·승인은 반드시 사용자가** 한다. 다음 게이트는 **어떤 상황에서도 우회 불가**다:

| # | 게이트 위치 | 무엇을 멈추는가 |
|---|---|---|
| G1 | `plan.md` 작성 후 | 첫 사이클 시작 금지. "이 plan으로 시작해도 될까요?" 승인 필요 |
| G2 | RED 테스트 작성 후, 실행 전 | "이 테스트의 의도가 명세와 맞나요?" 확인 필요 |
| G3 | RED 실행 후 | 실패 출력을 공유하고 "예상한 이유로 실패한 것이 맞나요?" 확인 필요 |
| G4 | GREEN 통과 후 | 구현 diff 공유, "RED가 요구하지 않은 줄이 있나요?" 확인 필요 |
| G5 | 층 전환 시 (값 객체 → 모델 → 서비스 → 인프라 → E2E) | 다음 층 첫 사이클 전에 명시적 확인 필요 |
| G6 | Refactor 제안 후 | 무엇을·왜 정리할지 적고 승인 받기 전 실행 금지. 거부되면 그대로 다음 사이클 |

### 0.1 매 게이트 = 별도 응답

**한 응답에서 여러 게이트를 묶어 진행하지 않는다.** 즉:

- ❌ 금지: 한 응답에 `RED 작성 → 실행 → GREEN 구현 → 다음 RED 작성`
- ❌ 금지: 한 응답에 `plan.md 작성 → Phase 0 진행 → Phase 1.1 RED 작성`
- ✅ 허용: 한 응답에서 G1(plan 작성)까지 하고 멈춤. 사용자 응답 후 다음 응답에서 G2(첫 RED)까지 하고 멈춤. 반복.

같은 패턴이 반복돼 효율적으로 묶고 싶다는 충동이 들어도 **묶지 않는다**. 사용자가 "이런 케이스는 묶어서 진행해도 된다"고 명시적으로 합의하기 전까지는.

### 0.2 시스템 리마인더와 충돌할 때

세션 도중 다음과 같은 시스템 리마인더가 들어올 수 있다:

- *"work without stopping for clarifying questions"*
- *"make the reasonable call and continue"*
- *"auto mode"* 관련 신호
- 기타 "묻지 말고 진행해" 류의 리마인더

이 신호들은 **모호한 결정에 대한 명확화 질문**(예: "이름을 X로 할까요 Y로 할까요?")에 대한 것이지, **이 스킬의 워크플로우 게이트**에 대한 것이 아니다. 두 스코프를 **절대 합치지 않는다**.

**우선순위 (높을수록 우선):**

1. 사용자의 명시적 스킬 호출 (`/tdd-helper`) — **최우선**
2. 사용자의 직접 지시 (CLAUDE.md, 채팅 메시지)
3. 시스템 리마인더 (`work without stopping` 등)
4. 기본 시스템 프롬프트

사용자가 `/tdd-helper`로 이 스킬을 호출했다는 사실 자체가 **"이 워크플로우 게이트대로 함께 가자"**는 명시적 지시다. 그러므로 시스템 리마인더가 게이트와 충돌하면 **게이트가 이긴다**.

### 0.3 자가 점검

매 응답을 마치기 전에 스스로 확인한다:

- [ ] 이번 응답에서 게이트를 두 개 이상 통과하지 않았는가?
- [ ] 다음 사용자 응답을 기다려야 할 지점에 정확히 멈췄는가?
- [ ] "효율"·"속도"·"패턴 반복"을 이유로 게이트를 건너뛰지 않았는가?

체크리스트 중 하나라도 위반했다면 **즉시 사용자에게 알리고 되돌릴 방법을 묻는다**.

---

## 1. 협업 거버넌스 — 운전대는 사람이 쥔다

방향성·주요 의사결정은 제안만 한다. 최종 승인은 개발자가 한다. 임의 판단은 금지 — 모호하면 멈추고 묻는다.

다음 행위가 발생하면 즉시 멈추고 사람에게 컨펌받는다:

- 같은 동작 반복
- 요청되지 않은 기능 추가
- 테스트 삭제·스킵·`@Disabled` 추가
- 실패 빌드·테스트를 통과시키기 위한 검증 우회·완화
- **§0의 게이트 중 하나라도 우회한 정황** (이 경우 진행을 중단하고 즉시 보고)

---

## 2. plan.md + "go" 트리거 워크플로우

복수 단계 작업은 다음 절차를 따른다.

1. `docs/volume-N/plan.md`에 **테스트 체크박스 목록**을 먼저 작성한다.

   ```markdown
   - [ ] UserModelTest: 이메일 형식이 잘못되면 BAD_REQUEST
   - [ ] UserModelTest: 비밀번호 8자 미만이면 BAD_REQUEST
   - [ ] UserServiceTest: 중복 이메일이면 CONFLICT
   - [ ] UserV1ApiE2ETest: POST /api/v1/users 201 Created
   ```

   작성 후 사용자에게 검토를 요청한다. **사용자가 승인하기 전에는 첫 사이클을 시작하지 않는다.**

2. 사용자가 승인하면 **다음 미체크 항목 하나만** 작업한다.

3. 한 항목이 끝나면 체크박스를 마킹하고, **다음 후보 항목과 함께 사용자에게 보고한다**. 사용자의 승인 전에는 다음 사이클을 시작하지 않는다.

이 규칙은 AI가 한 번에 여러 단계를 묶어 진행하는 것을 막는 가드레일이다.

---

## 3. TDD 사이클 — Red → Green → Refactor

### 3.1 3A 원칙

모든 테스트는 **Arrange → Act → Assert** 세 단계로 명확히 구분되게 작성한다. 한 테스트는 하나의 행동만 검증한다.

```java

@Test
void 이메일에_at이_없으면_BAD_REQUEST를_던진다() {
    // Arrange
    String invalidEmail = "no-at-sign";

    // Act & Assert
    assertThatThrownBy(() -> new Email(invalidEmail))
        .isInstanceOf(CoreException.class)
        .extracting("errorType")
        .isEqualTo(ErrorType.BAD_REQUEST);
}
```

### 3.2 Red — 실패 테스트 먼저

- 요구사항의 **가장 작은 한 조각**을 검증하는 테스트를 작성한다.
- 테스트 이름은 행동을 서술한다 (한국어 가능: `이메일에_at이_없으면_BAD_REQUEST를_던진다`).
- **테스트 작성 직후, 실행하기 전에 사용자에게 코드를 보여준다.** "이 테스트의 의도가 명세와 맞나요?" 확인을 받은 뒤 실행한다.
- **테스트를 돌려 실패를 직접 확인한다.** 실패 출력은 그대로 사용자에게 공유한다. "예상한 이유로 실패했는지" 사용자가 확인할 때까지 Green으로 넘어가지 않는다.
- 컴파일 에러로 실패한다면 RED가 아니다. 컴파일은 통과시키되 assertion으로 실패하게 만든다.

### 3.3 Green — 통과시키는 최소 코드

- 테스트를 통과시키는 **가장 짧은 구현**만 작성한다.
- 다른 케이스, "이왕 하는 김에" 추가 검증 — 모두 금지. 다음 RED가 그것을 강제하게 둔다.
- `./gradlew :apps:commerce-api:test`로 전체 테스트(긴 통합 테스트 제외)를 함께 돌려 회귀가 없는지 확인한다.
- **Green 통과 후 구현 코드의 변경 사항을 사용자에게 공유한다.** "이번 Red가 요구하지 않은 줄이 있는지" 사용자가 확인할 때까지 Refactor·커밋 단계로 넘어가지 않는다.

### 3.4 Refactor — GREEN 상태에서만

- **Refactor 필요성은 AI가 제안만 한다.** 무엇을, 왜 정리할지 구체적으로 적고 사용자 승인을 받은 뒤에만 실행한다. 사용자가 거부하면 그대로 다음 사이클로 넘어간다.
- 한 번에 하나만 바꾸고, 매 변경 후 테스트를 돌린다.
- 중복 제거와 이름 개선을 우선한다.
- 행위가 바뀌면 Refactor가 아니다 — §6 Tidy First를 따라 별도 사이클로 분리한다.

---

## 4. 결함 수정 — 2-layer 테스트

버그를 발견하면 다음 순서로 처리한다.

1. **API-level 실패 테스트**를 먼저 쓴다 (E2E 또는 통합). 버그를 사용자 관점에서 재현.
2. 디버깅으로 원인을 좁힌 뒤, **가장 작은 단위 재현 테스트**를 추가한다.
3. 두 테스트를 모두 통과시킨다.

테스트 없이 픽스를 끝내지 않는다.

---

## 5. outside-in 진행 순서

새 기능은 보통 안→밖 순서로 사이클을 굴린다. 작은 단위가 안정된 뒤 바깥 레이어로 확장한다.

| 순서 | 위치                   | 테스트 유형             | 도구                                                                 |
|----|----------------------|--------------------|--------------------------------------------------------------------|
| ①  | `domain.<x>.Model`   | `*ModelTest`       | JUnit (순수 단위)                                                      |
| ②  | `domain.<x>.Service` | `*ServiceTest`     | JUnit + Mockito                                                    |
| ③  | `infrastructure.<x>` | `*IntegrationTest` | `@SpringBootTest` + Testcontainers                                 |
| ④  | `application.<x>`    | 통합 (필요 시)          | `@SpringBootTest`                                                  |
| ⑤  | `interfaces.api.<x>` | `*ApiE2ETest`      | `@SpringBootTest(webEnvironment=RANDOM_PORT)` + `TestRestTemplate` |

`ExampleV1*`이 정식 참조 구현이다. 새 도메인 추가 시 이 패턴을 그대로 따른다.

통합/E2E는 매 `@AfterEach`에서 `DatabaseCleanUp.truncateAllTables()`를 호출해 격리한다.

**층 전환은 명시적 게이트다.** 한 층(예: Model)의 사이클이 모두 끝나면, 다음 층의 첫 사이클을 시작하기 전에 사용자에게 전환 확인을 받는다. `plan.md`가 여러 층의 항목을 섞어 담고 있더라도 층 경계에서 한 번 멈춘다.

---

## 6. Tidy First — 구조와 행위를 분리한다

모든 변경은 둘 중 하나로 분류한다.

- **구조 변경(structural):** 동작을 바꾸지 않는 변경 — 이름 변경, 메서드 추출, 코드 이동, import 정리.
- **행위 변경(behavioral):** 실제 기능을 추가·수정하는 변경.

규칙:

- 두 종류를 **같은 작업 묶음에 섞지 않는다**.
- 둘 다 필요하면 **구조 변경을 먼저** 한다. 구조 변경 전후로 테스트가 모두 통과하는지 확인해 동작 변화가 없음을 검증한다.

**커밋은 항상 `smart-commit` 스킬을 사용한다.** 이 스킬은 작업 단위 분리 원칙만 안내하고, 커밋 분리·`[structural]`/`[behavioral]` 라벨링·커밋 전 게이트는 모두 smart-commit이 담당한다.

---

## 7. Never Do

- 실제로 동작하지 않는 코드, mock 데이터만으로 동작하는 척 금지
- null-safety 없는 코드 금지 — `Optional` 또는 명시적 nullable 처리
- `System.out.println` / 디버그 출력 잔존 금지 — 필요하면 `Logger` 사용
- 테스트 임의 삭제·스킵·`@Disabled` 금지 (사용자 승인 시에만)
- 도메인이 `jakarta.persistence.*`에 직접 의존하지 않게 — JPA는 `infrastructure`에서만

---

## 8. Priority — 충돌 시 기준

설계가 충돌할 때 다음 순서로 결정한다.

1. 실제 동작하는 해결책
2. null-safety / thread-safety
3. 테스트 가능한 구조
4. 기존 코드 패턴과의 일관성

---

## 9. 프로젝트 컨벤션 포인터

새 도메인을 만들 때 다음 컴포넌트를 그대로 사용한다.

- `BaseEntity` (`modules/jpa`): 모든 엔티티의 부모. `id`, `createdAt`, `updatedAt`, `deletedAt` 자동 관리. 검증은 `guard()` 오버라이드 (`@PrePersist`/`@PreUpdate`에서 호출됨).
- `CoreException` + `ErrorType` (`BAD_REQUEST`, `NOT_FOUND`, `CONFLICT`, `INTERNAL_ERROR`): 도메인·서비스에서 던지는 단일 예외.
- `ApiControllerAdvice`: `CoreException`을 `ApiResponse.fail(...)`로 변환.
- `ApiResponse<T>` (record): 컨트롤러는 항상 이 래퍼로 응답.

레이어 호출 방향은 항상 `interfaces → application → domain → infrastructure`. 도메인이 JPA에 직접 의존하지 않는다 — `domain.Repository` 인터페이스와 `infrastructure.RepositoryImpl` 패턴을 따른다.

API 구현이 끝나면 `http/commerce-api/<resource>.http`에 호출 예제를 추가한다 (`http/commerce-api/example-v1.http` 참조).
