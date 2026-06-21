# Docs Index

이 문서는 현재 4주차 구현 작업의 문서 지도다. 1/2/3주차 문서는 과거 참고용으로만 둔다.

## 진입점

- 1순위 진입점은 `AGENTS.md`다.
- 이 문서는 `AGENTS.md`에서 연결되는 문서 지도 역할만 한다.

## 탐색 순서

| 순서 | 문서 | 목적 |
| --- | --- | --- |
| 1 | `AGENTS.md` | 최상위 작업 규칙, 커밋 규칙, 탐색 순서 |
| 2 | `.docs/design-review.md` | 빠진 설계 항목, 다음 질문 순서 |
| 3 | `.docs/worklog.md` | 현재 작업 상태와 재개 지점 |
| 4 | `.docs/domain.md` | 도메인 용어, 상태명, 모듈 소속 |
| 5 | `.docs/architecture.md` | 5계층 우선 패키지 구조, 도메인 경계, Onion/Hexagonal/CQRS, JPA 분리 기준 |
| 6 | `.docs/dto-spec.md` | API DTO 계약과 페이지 응답 형태 |
| 7 | `.codeguide/loopers-4-week.md` | 현재 구현 과제 기준 |
| 8 | `.codeguide/transaction-analysis.md` | 트랜잭션과 락 점검 가이드 |
| 9 | `.codeguide/loopers-3-week.md` | 3주차 구현 legacy reference |
| 10 | `.codeguide/service.md` | 전체 서비스 API 요구사항 기준 |
| 11 | `.codeguide/loopers-2-week.md` | 2주차 설계 legacy reference |
| 12 | `.codeguide/loopers-1-week.md` | 1주차 회원/인증 legacy reference |
| 13 | `.docs/design/01-requirements.md` | 설계 이력 및 요구사항 분석 |
| 14 | `.docs/design/02-sequence-diagrams.md` | 설계 이력 및 시퀀스 다이어그램 |
| 15 | `.docs/design/03-class-diagram.md` | 설계 이력 및 클래스 설계 |
| 16 | `.docs/design/04-erd.md` | 설계 이력 및 ERD |

## 현재 기준

- 현재 주차는 4주차 구현 단계다.
- 4주차 구현 기준은 `.codeguide/loopers-4-week.md`, `.docs/domain.md`, `.docs/architecture.md`, `.docs/worklog.md`를 우선한다.
- 1/2/3주차 가이드는 삭제하지 않고 legacy reference로 유지한다.
- 도메인 구현은 5계층 하위의 `catalog`, `coupon`, `ordering`, `payment`, `event` 경계와 POJO domain entity / infrastructure `*JpaEntity` 분리를 기준으로 한다.
- 4주차 필수 설계는 재고, 쿠폰, 주문의 RDB 트랜잭션 정합성과 동시성 제어다.
- 결제 worker, Outbox, 0원 주문 처리는 기존 확장 설계로 분리해 관리한다.

## 설계 이력

volume-2 설계 제출 이력은 아래 4개 파일에 남아 있다.
현재 4주차 설계 기준으로 덮어쓰지 않는다.

- `.docs/design/01-requirements.md`
- `.docs/design/02-sequence-diagrams.md`
- `.docs/design/03-class-diagram.md`
- `.docs/design/04-erd.md`

## 보조 문서

아래 문서는 설계 진행과 AI 탐색을 돕기 위한 보조 문서이며 제출 커밋에는 포함하지 않는다.

- `AGENTS.md`
- `.docs/README.md`
- `.docs/design-review.md`
- `.docs/worklog.md`
- `.docs/domain.md`
- `.docs/architecture.md`
- `.docs/dto-spec.md`
- `.codeguide/loopers-1-week.md`
- `.codeguide/loopers-2-week.md`
- `.codeguide/loopers-3-week.md`
- `.codeguide/loopers-4-week.md`
- `.codeguide/transaction-analysis.md`
- `.codeguide/service.md`

## 탐색 규칙

- 설계 누락을 찾을 때는 `.docs/design-review.md`를 먼저 본다.
- 재개 위치를 찾을 때는 `.docs/worklog.md`를 먼저 본다. 이 파일은 누적 로그가 아니라 최신 스냅샷으로 유지한다.
- 이름이 흔들릴 때는 `.docs/domain.md`를 먼저 본다.
- 구조가 흔들릴 때는 `.docs/architecture.md`를 먼저 본다.
- DTO 모양이 흔들릴 때는 `.docs/dto-spec.md`를 먼저 본다.
- 현재 구현 기준이 흔들릴 때는 `.codeguide/loopers-4-week.md`를 먼저 본다.
- 전체 서비스 API 기준이 흔들릴 때는 `.codeguide/service.md`를 본다.
- 2주차 설계 제출 맥락이 필요할 때만 `.codeguide/loopers-2-week.md`를 본다.
- 회원/인증 맥락이 필요할 때만 `.codeguide/loopers-1-week.md`를 본다.
