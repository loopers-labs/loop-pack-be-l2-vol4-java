# Design Review

이 문서는 현재 3주차 구현의 설계/구현 정합성 검토 문서다. 제출 커밋에는 포함하지 않는다.

## 검토 기준

- 현재 주차는 3주차 구현 단계다.
- `.docs/design`의 4개 파일은 volume-2 설계 이력으로 유지한다.
- 목표 아키텍처는 5계층 우선 패키지 구조 안에 도메인 모듈 경계를 두는 모듈러 모놀리스다.
- 현재 구현에는 `Point`/포인트 도메인을 포함하지 않는다.
- 구현 대상 도메인은 JPA/Spring 의존이 없는 POJO 도메인 엔티티와 infrastructure `*JpaEntity`를 분리한다.

## 현재 충분한 부분

| 영역 | 판단 |
| --- | --- |
| 요구사항 범위 | 상품, 브랜드, 좋아요, 주문, 재고, 결제, 외부 연동 흐름이 잡혀 있다. |
| 시퀀스 | 상품 상세, 좋아요, 주문/결제, 이벤트 전송 흐름이 2개 이상 충분히 작성되어 있다. |
| 클래스 설계 | 핵심 모델과 서비스 책임이 분리되어 있다. |
| ERD | 주요 테이블과 관계, unique/check 제약 방향이 들어 있다. |
| 포인트 제외 | 제출 문서에는 포인트 도메인이 없다. |
| `User` 경계 | 기존 `identity` 모듈의 `userId` 참조로만 다루기로 확정했다. |
| 목표 아키텍처 | 5계층 우선 패키지 구조와 도메인 모듈 경계를 현재 구현 기준으로 확정했다. |
| `StockService` 책임 | `catalog` 모듈의 별도 도메인 서비스로 확정했다. |
| 좋아요 컨트롤러명 | 도메인명과 맞춰 `ProductLikeController`로 확정했다. |
| Outbox 실패 정책 | 실패 시 `retry_count`를 증가시키고, 최대 재시도 초과 시 `FAILED`로 확정한다. |
| API 계약 | public API, 내부/외부 계약, 주요 에러 매핑을 요구사항 문서에 반영했다. |
| 상태 전이표 | 주문, 결제, Outbox 상태 전이표를 제출 문서에 반영했다. |
| 트랜잭션 경계표 | 유스케이스별 DB 트랜잭션 포함/제외 작업을 시퀀스 문서에 반영했다. |
| 테스트 계획 | 단위/통합/E2E 우선순위를 `.docs/test-plan.md`에 분리했다. |
| 내 좋아요 목록 API 경로 | `service.md` 원문을 따라 `GET /api/v1/users/{userId}/likes`로 확정했고, path `userId`와 `X-Loopers-LoginId` 일치 검증을 반영했다. |
| 주문 목록 조회 | `service.md` 원문을 따라 `GET /api/v1/orders?startAt&endAt`를 설계 API 계약, 시퀀스, 클래스 책임, DTO에 반영했다. |
| 상품 목록 페이징/정렬 | `service.md` 기준 `page=0`, `size=20`, `sort=latest/price_asc/likes_desc`를 반영했다. |
| ADMIN API | `/api-admin/v1`, `X-Loopers-Ldap`, 브랜드/상품/주문 ADMIN API와 운영 정책을 반영했다. |
| 인증 헤더 | 유저 API는 `X-Loopers-LoginId`, `X-Loopers-LoginPw`, ADMIN API는 `X-Loopers-Ldap`를 사용하도록 반영했다. |
| 쿠폰 범위 | `service.md`에 구체 API가 없으므로 이번 설계에서는 주문 확장 포인트로 명시했다. |
| 사용자 행동 기록 | 좋아요 이력, 주문/주문항목, 주문 완료 outbox를 기준 기록으로 두고 상품 조회 이벤트는 향후 event 확장 포인트로 명시했다. |
| ADMIN 상품/브랜드 삭제와 주문 이력 | 상품 삭제는 물리 삭제하지 않고 `ProductStatus.STOPPED`으로 전환하며, 브랜드 삭제도 `Brand.deletedAt` 표시와 관련 상품 `STOPPED` 전환으로 처리하는 soft-delete 정책으로 확정했다. |
| 주문 조회 응답의 `paymentStatus` | 주문 생성 트랜잭션에서 `payment(order_id, status=REQUESTED)` row를 함께 생성해 주문 목록/상세 응답의 `paymentStatus`가 항상 존재하도록 확정했다. `Order`와 `Payment`는 생성 성공 후 1:1 관계다. |
| 유저 API 시퀀스 인증 헤더 | user_required 시퀀스마다 `X-Loopers-LoginId`와 `X-Loopers-LoginPw` 검증 단계를 명시했다. |
| 3주차 포인트 요구 제거 | 포인트 차감/포인트 부족은 이번 구현 범위에서 제외하고 3주차 가이드 체크리스트에서 제거했다. |
| 도메인/JPA 분리 | 3주차 구현 대상은 POJO 도메인 엔티티와 infrastructure JPA 엔티티/adapter 매핑 구조로 정리했다. |
| 1/2주차 문서 축약 | `.codeguide/loopers-1-week.md`, `.codeguide/loopers-2-week.md`는 legacy reference로 축약하고 현재 기준은 3주차 문서로 이동했다. |

## 빠진 부분 또는 보강 필요

| 우선순위 | 항목 | 이유 | 반영 위치 |
| --- | --- | --- | --- |
| 1 | 없음 | PR 리뷰 위험 후보 3개를 모두 설계 문서와 보조 문서에 반영했다. | - |

## 처리 순서

1. 없음

## 다음 질문 후보

- 없음
