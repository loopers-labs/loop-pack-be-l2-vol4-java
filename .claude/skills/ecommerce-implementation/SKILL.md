# Ecommerce Implementation Skill

## Purpose

이 Skill은 확정된 이커머스 요구사항과 설계 산출물을 기반으로 `apps:commerce-api` 중심의 Spring Boot 구현을 수행할 때 사용한다.

## 사용 조건

- 회원, 브랜드, 상품, 좋아요, 쿠폰, 주문, 결제 기능을 구현하거나 수정할 때
- 사용자 API 와 관리자 API 를 코드로 분리할 때
- JPA Entity, Repository, QueryDSL, 논리 삭제, 주문 상품 스냅샷을 구현할 때
- 주문 생성, 재고 차감, 쿠폰 사용, 결제 상태 변경의 트랜잭션을 구현할 때
- 재고 차감, 좋아요, 쿠폰 발급의 동시성 제어와 테스트가 필요할 때

## 작업 전 확인

1. `.docs/design/01-requirements.md` 의 기능 ID, 유스케이스, 핵심 정책을 확인한다.
2. 시퀀스, 클래스, ERD 설계 문서가 있으면 먼저 확인한다.
3. 설계와 구현이 다르면 확인 필요 항목으로 분리한다.
4. 변경 대상 모듈은 기본적으로 `apps:commerce-api` 이다.
5. Kafka 소비/발행이 포함되면 `apps:commerce-streamer`, `modules:kafka` 영향도 함께 확인한다.
6. 배치 처리가 포함되면 `apps:commerce-batch` 영향도 함께 확인한다.

## Workflow

1. 변경 대상 도메인과 API 범위를 식별한다.
2. Controller/Facade/Domain/Infrastructure 레이어 책임을 나눈다.
3. 트랜잭션 경계를 먼저 정한다.
4. 영속성 모델, 논리 삭제, 주문 상품 스냅샷을 구현한다.
5. 재고/좋아요/쿠폰 동시성 위험을 점검한다.
6. 단위 테스트와 통합 테스트를 추가한다.

## 세부 지침

- [레이어링](layering.md)
- [영속성](persistence.md)
- [동시성](concurrency.md)
- [트랜잭션](transaction.md)
- [테스트](testing.md)

## 공통 규칙

- 패키지/레이어 책임과 테스트 원칙은 `.claude/rules/code-conventions.md` 를 따른다.
- 모듈 책임과 공통 모듈 수정 기준은 `.claude/rules/module-structure.md` 를 따른다.
- 설계 산출물 작성 위치와 PlantUML 규칙은 `.claude/rules/design-docs.md` 를 따른다.
