# CLAUDE.md

## 1. 프로젝트

- Loopers Commerce — 멀티모듈 Spring Boot 백엔드
- **Round 3 작업 범위**: `apps/commerce-api` 의 **Product / Brand / Like / Order 도메인**
- 이전 라운드(Round 1)에서 User 도메인 완료. Round 2 에서는 설계 문서 작성.
- 도메인 추가 시 이 파일도 함께 업데이트.

## 2. 기술 스택

Java 21, Spring Boot 3.4.4, Gradle 8.14.4 (Kotlin DSL), JPA + MySQL Testcontainers, JUnit 5 + AssertJ.
세부 버전은 `gradle.properties`, `build.gradle.kts` 참조.

## 3. 작업 위치

- 새 코드: `apps/commerce-api/src/main/java/com/loopers/{interfaces|application|domain|infrastructure}/<도메인>/`
  - 이번 라운드 도메인: `product`, `brand`, `like`, `order`
- 테스트: `src/test/java/com/loopers/domain/<도메인>/` (단위/통합), `src/test/java/com/loopers/interfaces/api/` (E2E)
- `example/` 패키지는 도메인 레이어 패턴 참고용. **수정 금지.**
- `user/` 패키지는 4-layer 풀 구현 참고용. **수정 금지** (Round 1 완료 코드).

## 4. 개발 원칙 — TDD

> Kent Beck 의 *Augmented Coding* 시스템 프롬프트 참고.

- **Red → Green → Refactor** 사이클 엄수
- 실패하는 가장 작은 테스트부터 작성
- 통과시킬 **최소 코드**만 작성 (오버엔지니어링 금지)
- 리팩토링은 모든 테스트가 통과하는 상태에서만
- 테스트는 **3A** 구조 (Arrange — Act — Assert)
- 테스트 이름은 동작 묘사 (예: `throwsBadRequest_whenLoginIdIsTooLong`)
- 한 번에 테스트 하나씩

## 5. 협업 원칙

- 방향성 결정은 개발자, 클로드는 옵션 제안만.
- 클로드는 다음 행동 시 **즉시 멈추고 개발자에게 확인**:
  - 요청하지 않은 기능 구현
  - 테스트 비활성화/삭제
  - 같은 시도 반복 실패

## 6. Never Do

- 동작하지 않는 코드, 가짜 Mock 구현 금지
- null-unsafe 코드 금지 — null 가능한 자리는 `Optional<T>` 사용
- `System.out.println` 같은 디버그 출력 금지
- 테스트 임의 삭제 / `@Disabled` 처리 금지

## 7. 도메인 & 객체 설계 전략

- 도메인 객체는 자기 상태를 스스로 책임진다. (예: `Product`가 재고를 차감, `User`가 포인트를 차감)
- **Entity** 는 식별자(ID)와 상태 변화의 연속성을 갖는다. 동일성은 ID로 판단한다.
- **Value Object** 는 불변(immutable)이며, 같은 값이면 같은 객체로 본다. (예: `Money`, `Quantity`)
  - 값에 도메인 규칙(음수 금지, 범위 등)이 있으면 VO 후보다.
- **Domain Service** 는 상태가 없으며, 단일 도메인 객체로 해결되지 않는 **여러 객체의 협력 로직**만 담는다.
- `XxxManager`, `XxxHelper`, `XxxProcessor` 같은 doer 클래스로 도메인 로직을 빼돌리지 않는다.
  - 정 필요하면 개발자에게 먼저 확인.
- 같은 규칙이 여러 Service 에 반복되면 도메인 객체(Entity/VO)로 끌어올린다.
- 책임 분배와 결합도에 대한 의사결정은 개발자가 한다. 클로드는 선택지 2개와 트레이드오프를 제시한다.

## 8. 아키텍처 & 패키지 구성 전략

- 레이어드 아키텍처 + DIP 를 따른다.
- 의존 방향: `Interfaces → Application → Domain ← Infrastructure`
  - Domain 은 다른 어떤 레이어도 의존하지 않는다. Spring 의존도 최소화.
- **Repository 인터페이스는 Domain Layer**, **구현체는 Infrastructure Layer**.
- API request/response DTO 와 Application Layer 의 입/출력 DTO 는 분리한다.
- 패키지는 `/<레이어>/<도메인>` 으로 구성한다.
  - 예: `/domain/product`, `/application/order`, `/infrastructure/like`, `/interfaces/api/brand`
- Application Layer 는 **흐름 조율(orchestration)** 만 담당. 비즈니스 규칙은 Domain 으로 위임.
- 외부 시스템(JPA/Redis/HTTP 등) 호출은 반드시 Infrastructure 를 통해서만.

## 9. Recommendation

- API 검증은 **TestRestTemplate 기반 E2E** 우선
- 개발 완료된 API 는 `http/commerce-api/*.http` 에 정리
- 기존 패턴 (`example/`, `user/`) 분석 후 일관성 유지
