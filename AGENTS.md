## 1. 커밋 규칙
- 커밋 메시지는 한국어로 작성한다.
- volume-2 설계 제출 커밋에는 `.docs/design` 디렉토리의 아래 4개 파일만 포함한다.
  - `.docs/design/01-requirements.md`
  - `.docs/design/02-sequence-diagrams.md`
  - `.docs/design/03-class-diagram.md`
  - `.docs/design/04-erd.md`
- `AGENTS.md`, `.codeguide/**`, 도메인 용어집, 구현 계획 문서는 volume-2 설계 제출 커밋에 포함하지 않는다.
- 현재 브랜치는 6주차 구현 흐름이므로, 구현 검증과 문서 기준은 `.codeguide/loopers-6-week.md`, `.docs/domain.md`, `.docs/architecture.md`, `.docs/worklog.md`를 우선한다.

## 2. 진행 Workflow - 증강 코딩
- 방향성 및 주요 의사 결정은 개발자에게 제안할 수 있으며, 최종 승인된 사항을 기반으로 작업을 수행한다.
- AI가 반복적인 동작, 요청하지 않은 기능 구현, 테스트 삭제를 임의로 진행할 경우 개발자가 개입할 수 있도록 중간 결과를 보고한다.
- AI는 임의 판단을 피하고, 방향성 제안 후 승인된 범위에서 작업한다.
- 애매한 요구사항은 추측하지 말고 선택지와 영향도를 먼저 제시한다.

## 3. 개발 Workflow - TDD
- 모든 테스트는 3A 원칙으로 작성한다. (Arrange - Act - Assert)
- Red Phase: 요구사항을 만족하는 실패 테스트를 먼저 작성한다.
- Green Phase: 실패 테스트를 통과하는 최소 코드를 작성한다.
- Refactor Phase: 불필요한 코드 제거와 품질 개선을 수행하고 모든 테스트가 통과해야 한다.
- 불필요한 private 함수는 지양하고 객체지향적으로 작성한다.
- unused import를 제거한다.
- 성능 최적화가 필요하면 대안을 함께 제안한다.

## 4. Never Do
- 실제 동작하지 않는 코드나 불필요한 Mock 데이터를 이용한 구현을 하지 않는다.
- null-safety를 무시하지 않는다. Java에서는 필요 시 `Optional`을 활용한다.
- `println` 코드를 남기지 않는다.
- 사용자의 명시 승인 없이 Redis, Kafka, cache, message broker 같은 저장/연동 기술을 새로 도입하지 않는다.

## 5. 설계 진행 규칙
- 설계 문서를 수정하기 전 핵심 질문을 한 줄씩 묻고, 답변을 받은 뒤 반영한다.
- 도메인명, 상태명, API명, 메서드명은 도메인 용어집과 일치시킨다.
- 도메인명이나 상태명이 바뀌면 `.docs/domain.md`를 먼저 수정한 뒤 관련 문서와 코드에 반영한다.
- 아키텍처 경계가 바뀌면 `.docs/architecture.md`를 먼저 수정한 뒤 관련 문서와 코드에 반영한다.
- `.docs/design`에는 제출 대상 4개 파일 외의 파일을 추가하지 않는다.

## 6. 아키텍처 결정
- 장기적으로 큰 서비스를 만든다는 전제로 도메인 우선 모듈러 모놀리스로 설계한다.
- 본 프로젝트는 레이어드 아키텍처를 따르며 DIP를 준수한다.
- 현재 구현 대상은 Onion/Hexagonal/CQRS 방향을 적극 적용한다.
- 최상위 패키지는 기존 5계층인 `interfaces`, `application`, `domain`, `infrastructure`, `support`를 유지한다.
- 도메인 경계는 각 계층 하위 패키지의 `catalog`, `coupon`, `ordering`, `payment`, `event`로 나눈다.
- 예: `com.loopers.domain.catalog.product`, `com.loopers.application.ordering.order`, `com.loopers.interfaces.api.catalog.product`.
- 도메인 레이어는 JPA, Spring, HTTP 타입에 직접 의존하지 않는다.
- 영속성 객체는 infrastructure의 `*JpaEntity`로 분리하고 repository adapter에서 도메인 엔티티와 매핑한다.
- API request/response DTO와 application 레이어 DTO는 분리한다.
- 현재 구현 구조가 목표 구조와 다르면, 구현을 바로 바꾸지 말고 설계 문서에 목표 구조와 차이를 먼저 기록한다.

## 7. 도메인 & 객체 설계 전략
- 도메인 객체는 비즈니스 규칙을 캡슐화해야 한다.
- 애플리케이션 서비스는 서로 다른 도메인을 조립해 도메인 로직을 조정하고 기능을 제공한다.
- 규칙이 여러 서비스에 나타나면 도메인 객체에 속할 가능성이 높다.
- 각 기능에 대한 책임과 결합도는 개발자의 의도를 확인하고 개발을 진행한다.
- 실제 동작하는 해결책을 우선하고, null-safety와 thread-safety를 고려한다.
- 테스트 가능한 구조로 설계하고 기존 코드 패턴을 분석한 뒤 일관성을 유지한다.

## 8. 문서 탐색 순서
- 이 파일이 1순위 진입점이다. 설계나 구현 작업을 시작하면 반드시 `AGENTS.md`를 먼저 읽는다.
- 그 다음 아래 순서로 문서를 읽고, 하위 문서의 내용이 충돌하면 `AGENTS.md`의 규칙을 우선한다.
  1. `.docs/README.md`
  2. `.docs/design-review.md`
  3. `.docs/worklog.md`
  4. `.docs/domain.md`
  5. `.docs/architecture.md`
  6. `.codeguide/loopers-6-week.md`
  7. `.codeguide/loopers-5-week.md`
  8. `.codeguide/transaction-analysis.md`
  9. `.codeguide/loopers-4-week.md`
  10. `.codeguide/loopers-3-week.md`
  11. `.codeguide/service.md`
  12. `.codeguide/loopers-2-week.md`
  13. `.codeguide/loopers-1-week.md`
  14. `.docs/design/01-requirements.md`
  15. `.docs/design/02-sequence-diagrams.md`
  16. `.docs/design/03-class-diagram.md`
  17. `.docs/design/04-erd.md`

## 9. 문서 역할
- `.docs/README.md`: 문서 지도와 탐색 목적을 정의한다.
- `.docs/design-review.md`: 빠진 설계 항목과 다음 질문 순서를 관리한다.
- `.docs/worklog.md`: 언제 중단되어도 이어가기 위한 현재 작업 상태를 관리한다.
- `.docs/domain.md`: 도메인명, 상태명, 모듈 소속, 구현 이름의 기준이다.
- `.docs/architecture.md`: 장기 아키텍처 결정, 모듈 경계, Onion/Hexagonal/CQRS, JPA 분리 기준을 정의한다.
- `.codeguide/loopers-6-week.md`: 현재 6주차 PG 결제 연동과 Resilience 구현 과제 조건과 완료 기준이다.
- `.codeguide/loopers-5-week.md`: 5주차 조회 성능 개선 legacy reference다.
- `.codeguide/transaction-analysis.md`: 주문, 쿠폰, 재고, 결제, Outbox 트랜잭션 점검 기준이다.
- `.codeguide/loopers-4-week.md`: 4주차 트랜잭션 구현 legacy reference다.
- `.codeguide/loopers-3-week.md`: 3주차 구현 과제 legacy reference다.
- `.codeguide/service.md`: 전체 서비스 API 요구사항 기준이다.
- `.codeguide/loopers-2-week.md`: 2주차 설계 제출 legacy reference다.
- `.codeguide/loopers-1-week.md`: 기존 회원/인증 legacy reference다.
- `.docs/design/*`: volume-2 설계 이력 4개 문서다.

## 10. 작업 지속성 규칙
- 매 작업 단위가 끝날 때 `.docs/worklog.md`를 갱신한다.
- `.docs/worklog.md`에는 현재 브랜치, 최근 결정, 수정 파일, 제출 포함/제외 파일, 다음 작업을 남긴다.
- `.docs/worklog.md`는 누적 로그가 아니라 최신 스냅샷으로 유지한다.
- `.docs/worklog.md`는 180줄 이하를 목표로 하며, 길어지면 오래된 의사결정과 검증 내역을 요약하거나 제거한다.
- 검증 명령은 최근 3개와 실패 원인만 남기고, 상세 이력은 Git diff, 커밋, 대화 기록을 기준으로 추적한다.
- 사용자가 언제 세션을 종료해도 다음 작업자는 `AGENTS.md -> .docs/README.md -> .docs/design-review.md -> .docs/worklog.md` 순서로 읽고 이어간다.
- 제출 문서 수정 전후에는 `.docs/worklog.md`에 어떤 제출 파일을 건드렸는지 기록한다.

## 11. 저장 처리 고도화 협의 규칙
- 저장 처리 방식 고도화는 한 번에 한 가지 주제만 질문하고, 답변을 받은 뒤 다음 질문이나 구현으로 진행한다.
- 저장 처리 관련 코드를 수정하기 전 의도, 선택지, 영향 범위, 검증 방법을 먼저 설명한다.
- 트랜잭션 경계, 동시성 제어, 재고 차감/복구, 결제 멱등성, outbox 상태 전이, DB 제약조건/인덱스, 이력/감사 정책은 사용자와 합의 후 반영한다.
- 합의된 결정은 작업 단위 종료 시 `.docs/worklog.md` 최신 스냅샷에 반영한다.

## 12. API 및 검증 권장사항
- 개발 완료된 API는 가능하면 `.http/**.http`에 분류해 작성한다.
- 실제 API를 호출해 확인하는 E2E 테스트를 작성한다.
- 재사용 가능한 객체 설계를 우선한다.
- 성능 최적화가 필요한 경우 대안과 근거를 제안한다.
