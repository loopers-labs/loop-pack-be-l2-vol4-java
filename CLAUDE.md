# CLAUDE.md

## 1. 프로젝트

- Loopers Commerce — 멀티모듈 Spring Boot 백엔드
- **Round 1 작업 범위**: `apps/commerce-api` 의 **User 도메인** (회원가입 / 내 정보 조회 / 비밀번호 수정)
- 도메인 추가 시 이 파일도 함께 업데이트.

## 2. 기술 스택

Java 21, Spring Boot 3.4.4, Gradle 8.14.4 (Kotlin DSL), JPA + MySQL Testcontainers, JUnit 5 + AssertJ.
세부 버전은 `gradle.properties`, `build.gradle.kts` 참조.

## 3. 작업 위치

- 새 코드: `apps/commerce-api/src/main/java/com/loopers/{interfaces|application|domain|infrastructure}/user/`
- 테스트: `src/test/java/com/loopers/domain/user/` (단위/통합), `src/test/java/com/loopers/interfaces/api/` (E2E)
- `example/`, `product/` 패키지는 참고용. **수정 금지.**

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

## 7. Recommendation

- API 검증은 **TestRestTemplate 기반 E2E** 우선
- 개발 완료된 API 는 `http/commerce-api/*.http` 에 정리
- 기존 패턴 (`example/`, `product/`) 분석 후 일관성 유지
