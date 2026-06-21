---
name: loopers-transaction-analysis
description: Loopers Java/Spring 프로젝트의 4주차 트랜잭션 요구사항을 분석하고 개선한다. 주문, 재고, 쿠폰, 결제, Outbox 흐름에서 @Transactional, JPA 영속성 컨텍스트, 비관적 row lock, 롤백 경계, 트랜잭션 내부 외부 호출, 동시성 테스트를 점검하거나 구현할 때 사용한다.
---

# Loopers 트랜잭션 분석

## 핵심 절차

주문, 쿠폰, 재고, 결제, Outbox 관련 코드를 검토하거나 수정하기 전에 이 절차를 따른다.

1. `AGENTS.md`, `.docs/README.md`, `.docs/design-review.md`, `.docs/worklog.md`, `.docs/domain.md`, `.docs/architecture.md`, `.codeguide/loopers-4-week.md`, `.codeguide/transaction-analysis.md` 순서로 기준 문서를 읽는다.
2. `interfaces -> application -> domain -> infrastructure` 전체 호출 흐름을 추적한다. 단일 메서드만 보고 트랜잭션 적합성을 판단하지 않는다.
3. `@Transactional` 시작점, 포함된 쓰기 작업, read-only 조회, JPA flush 시점, 지연 로딩 가능성, row lock 획득 순서, 외부 시스템 호출 위치를 표로 정리한다.
4. 현재 코드가 `references/week4-transaction-checklist.md`의 4주차 기준과 맞는지 비교한다.
5. 문제를 발견하면 파일/라인, 영향, 최소 수정안, 검증 방법을 함께 제시한다.

## 필수 점검 기준

- 4주차 필수 범위와 기존 확장 설계를 분리해서 판단한다.
- 주문 생성의 핵심 원자성은 재고 차감, 발급 쿠폰 사용, 주문 스냅샷 저장이다.
- 주문 생성에서는 상품 row를 상품 ID 오름차순으로 먼저 잠그고, 필요하면 발급 쿠폰 row를 잠근다.
- 쿠폰 발급에서는 쿠폰 템플릿 row를 잠근 뒤 같은 트랜잭션에서 사용자별 발급 수를 확인한다.
- Controller의 `@Transactional`, 순수 조회를 감싼 쓰기 트랜잭션, 쓰기 트랜잭션 내부 복잡 조회, 지연 로딩으로 인한 늦은 쿼리, DB 트랜잭션 내부 외부 PG/데이터 플랫폼 호출을 위험 신호로 본다.
- 단순 조회는 프로젝트 패턴에 맞춰 `@Transactional(readOnly = true)` 적용 여부를 확인한다.
- Redis, Kafka, cache, message broker, Flyway, 비 RDB 저장소 개선은 사용자 명시 승인 없이 제안만 하고 도입하지 않는다.

## 결과 작성 형식

리뷰나 분석 결과는 심각도 높은 문제부터 작성한다.

```text
문제: <위험 요약>
위치: <file:line>
영향: <롤백, 락, 정합성, 지연 위험>
권장 수정: <최소 수정안>
검증: <구체적인 테스트 또는 명령>
```

문제가 없으면 명확히 문제가 없다고 말하고, 남은 테스트 공백이나 환경상 검증 불가 항목만 기록한다.

구현 작업을 수행했다면 작업 종료 시 `.docs/worklog.md`에 최근 결정, 수정 파일, 검증 결과, 다음 트랜잭션 질문을 최신 스냅샷으로 남긴다.

## 참고 자료

- `references/week4-transaction-checklist.md`: 주문/쿠폰/재고 필수 트랜잭션, 기존 결제/Outbox 확장 흐름, 검증 테스트 체크리스트.
