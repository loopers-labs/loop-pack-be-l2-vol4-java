# Design Review

이 문서는 현재 7주차 구현의 설계/구현 정합성 검토 문서다. 제출 커밋에는 포함하지 않는다.

## 검토 기준

- 현재 주차는 7주차 구현 마무리 단계다.
- `.docs/design`의 4개 파일은 volume-2 설계 이력으로 유지한다.
- 7주차 필수 검토 범위는 ApplicationEvent, Kafka Producer/Consumer, Transactional Outbox, Kafka 기반 선착순 쿠폰 발급, 이벤트 파이프라인 메트릭이다.
- 4주차 주문/재고/쿠폰 트랜잭션 기준과 6주차 PG 결제 기준은 legacy reference이지만 현재 구현의 정합성 점검 기준으로 유지한다.
- 목표 아키텍처는 5계층 우선 패키지 구조 안에 도메인 모듈 경계를 두는 모듈러 모놀리스다.
- 구현 대상 도메인은 JPA/Spring 의존이 없는 POJO 도메인 엔티티와 infrastructure `*JpaEntity`를 분리한다.

## 7주차 핵심 설계

| 영역 | 판단 |
| --- | --- |
| 이벤트 분리 | 좋아요 등록/취소는 커맨드로 즉시 처리하고, 집계 갱신은 `ProductLikeChangedApplicationEvent`와 Outbox/Kafka로 분리했다. |
| Outbox | 기존 주문 전용 Outbox를 범용 `EventOutbox`로 전환하고, 도메인 상태 변경과 이벤트 저장을 같은 DB 트랜잭션에 포함한다. |
| Kafka relay | `EventRelayWorker`는 pending Outbox를 조회한 뒤 Kafka 발행을 수행하고, 성공/실패 결과는 별도 트랜잭션에서 반영한다. |
| Streamer 집계 | `commerce-streamer`는 `catalog-events`를 manual Ack로 소비하고 `event_handled(event_id PK)`와 최신 이벤트 시각 기준으로 `product_metrics`를 갱신한다. |
| 쿠폰 발급 요청 | `POST /api/v1/coupons/{couponId}/issue`는 `CouponIssueRequest(PENDING)`와 `coupon-issue-requests` Outbox를 같은 트랜잭션에 저장한다. |
| 쿠폰 발급 처리 | `CouponIssueRequestConsumer`는 eventId 멱등 처리 후 요청 row와 쿠폰 템플릿 row를 잠그고 전체 발급 한도와 사용자별 한도를 검사한다. |
| 결과 확인 | 사용자는 `GET /api/v1/coupons/issues/{requestId}` polling으로 `PENDING`/`SUCCEEDED`/`FAILED`를 확인한다. |
| 메트릭 | Outbox relay, Kafka consumer, product metrics update 지표를 Micrometer로 기록하고 `monitoring.yml`의 Prometheus endpoint 설정을 사용한다. |

## 유지되는 트랜잭션 기준

| 영역 | 현재 기준 |
| --- | --- |
| 쿠폰 발급 동시성 | 템플릿 row를 `PESSIMISTIC_WRITE`로 잠근 뒤 전체 발급 수와 사용자별 발급 수를 검사하고 발급 쿠폰을 저장한다. |
| 주문 동시성 | 상품 ID 오름차순으로 상품 row를 잠그고 재고를 차감한 뒤, 발급 쿠폰 row를 `PESSIMISTIC_WRITE`로 잠가 사용 처리한다. |
| 주문 원자성 | 재고 차감, 쿠폰 `USED` 전이, 주문 저장은 `OrderFacade.placeOrder()`의 단일 DB 트랜잭션에 포함한다. |
| 결제 복구 | 결제 실패, 취소, timeout 시 주문 row, 상품 row, 발급 쿠폰 row 순서로 잠그고 재고와 쿠폰을 복구한다. |
| 결제 worker | 외부 PG 호출을 DB 트랜잭션 밖으로 더 줄이는 구조는 후속 개선 목표로 남긴다. |

## 빠진 부분 또는 보강 필요

| 우선순위 | 항목 | 이유 | 반영 위치 |
| --- | --- | --- | --- |
| 1 | DLQ | 7주차 Nice-To-Have이며 현재 구현 범위에서 제외했다. | `.codeguide/loopers-7-week.md` |
| 2 | 전체 회귀 테스트 | 7주차 focused/E2E/동시성/런타임 메트릭 검증은 완료했지만, 전체 모듈 테스트까지는 실행하지 않았다. | `.docs/test-plan.md`, `.docs/worklog.md` |
| 3 | 환경별 worker/consumer 설정 | local 검증은 완료했지만 운영 환경별 relay/consumer 활성화 정책은 별도 배포 설정에서 확정해야 한다. | `.docs/worklog.md` |

## 처리 순서

1. 필요 시 DLQ 적용 여부를 결정한다.
2. 제출 전 전체 회귀 테스트 범위를 선택해 실행한다.
3. 운영 환경별 relay/consumer 활성화 설정을 분리한다.

## 다음 질문 후보

- 7주차 제출 전 DLQ를 추가할지, 운영 보강 과제로 남길지 결정한다.
