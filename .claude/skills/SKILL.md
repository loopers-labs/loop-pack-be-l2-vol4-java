# 이커머스 구현 공통 Skill

이 Skill 은 `apps:commerce-api` 에 이커머스 사용자/관리자 기능을 구현할 때 적용한다.
공통 규칙은 루트 `AGENTS.md`, `CLAUDE.md` 와 `.claude/rules/*` 를 우선 따르고, 이 문서는 기능 구현 시 도메인 경계와 세부 요구사항을 보완한다.

## 사용 조건

- 회원가입, 내정보 조회, 비밀번호 변경 기능을 구현하거나 수정할 때
- 브랜드/상품 조회, 등록, 수정, 삭제 기능을 구현하거나 수정할 때
- 상품 좋아요 등록/취소, 좋아요 상품 목록 조회 기능을 구현하거나 수정할 때
- 쿠폰 발급, 주문, 결제, 주문 관리자 기능을 구현하거나 수정할 때
- 주문 시점 상품 스냅샷, 재고 확인/차감, 유저 행동 기록 흐름을 설계하거나 검증할 때

## 작업 전 확인

1. 변경 대상 모듈은 기본적으로 `apps:commerce-api` 이다.
2. Kafka 기반 행동 기록/이벤트 발행 또는 컨슈밍이 포함되면 `apps:commerce-streamer`, `modules:kafka` 영향도 함께 확인한다.
3. 배치성 정산, 통계, 쿠폰 만료 처리 등이 포함되면 `apps:commerce-batch` 영향도 함께 확인한다.
4. 공통 인프라 설정 변경이 필요한 경우에만 `modules:jpa`, `modules:redis`, `modules:kafka` 를 수정한다.

## 기본 구현 흐름

1. 요구사항을 사용자 기능, 관리자 기능, 공통 도메인 규칙으로 분류한다.
2. 패키지/레이어 책임과 테스트 원칙은 `.claude/rules/code-conventions.md` 를 따른다.
3. 모듈 책임과 공통 모듈 수정 기준은 `.claude/rules/module-structure.md` 를 따른다.
4. 새 기능 또는 버그 수정에는 가능한 범위에서 테스트를 함께 추가한다.

## 설계 문서

- 설계 산출물 작성 위치, 표준 파일 구조, PlantUML 관리 규칙은 `.claude/rules/design-docs.md` 를 따른다.
- 기능 구현 전에 관련 설계 문서가 있으면 먼저 확인하고, 설계와 구현이 다르면 확인 필요 항목으로 분리한다.

## 세부 지침

- [도메인 경계 및 공통 설계](./domain-boundaries.md)
- [회원 기능](./member.md)
- [브랜드 및 상품 기능](./brand-product.md)
- [좋아요 기능](./like.md)
- [쿠폰, 주문, 결제 기능](./order-payment.md)
- [관리자 API 기능](./admin-api.md)
- [행동 기록 및 확장 이벤트](./event-tracking.md)
- [테스트 지침](./testing.md)
