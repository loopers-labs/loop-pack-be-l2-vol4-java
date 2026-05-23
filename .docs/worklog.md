# Worklog

이 문서는 설계 보조 문서다. volume-2 제출 커밋에는 포함하지 않는다.

## 재개 규칙

- 작업을 재개하면 `AGENTS.md`를 먼저 읽고, 그 다음 이 문서를 읽는다.
- 이 문서는 매 작업 단위가 끝날 때 갱신한다.
- 제출 커밋에는 `.docs/design`의 4개 파일만 포함한다.

## 현재 상태

| 항목 | 내용 |
| --- | --- |
| 브랜치 | `volume-2` |
| 현재 단계 | 제출 대상 4개 설계 문서 커밋 완료 |
| 제출 문서 수정 여부 | 커밋 `d461cd0`에 `.docs/design` 4개 파일만 포함 |
| 다음 핵심 질문 | 없음 |

## 최근 확정

- volume-2 제출 커밋에는 `.docs/design`의 4개 파일만 포함한다.
- 이번 주차는 설계에 집중하고, 구현 변경은 다음 주차로 넘긴다.
- 아키텍처는 도메인 우선 모듈러 모놀리스로 잡는다.
- 최상위 모듈은 `catalog`, `ordering`, `payment`, `event`로 나눈다.
- `ProductLike`는 `catalog` 모듈에 둔다.
- `User`는 volume-2의 새 도메인이 아니라 기존 `identity` 모듈의 `userId` 참조로만 다룬다.
- 제출 문서에 도메인 우선 모듈러 모놀리스 구조를 짧게 반영했다.
- `StockService`는 `catalog` 모듈의 별도 도메인 서비스로 둔다.
- 좋아요 API 컨트롤러 이름은 `ProductLikeController`로 맞춘다.
- Outbox 전송 실패 시 `retry_count`를 증가시키고, 최대 재시도 초과 시 `FAILED`로 확정한다.
- API 계약은 `method/path/header/request/response/error` 수준으로 요구사항 문서에 반영한다.
- 주문, 결제, Outbox 상태 전이표를 제출 문서에 반영한다.
- 유스케이스별 트랜잭션 경계표를 `02-sequence-diagrams.md`에 반영한다.
- 다음 주 구현용 테스트 계획을 별도 문서로 분리한다.
- 이번 주차 설계에는 `Point`/포인트 도메인을 포함하지 않는다.
- `AGENTS.md`를 1순위 진입점으로 두고, 보조 문서와 제출 문서를 바인딩한다.
- 매 작업 후 `.docs/worklog.md`를 갱신해 중단 후 재개 가능 상태를 유지한다.
- `.codeguide/service.md`는 제출 제외 보조 가이드로 유지하고, 내용 변경 없이 읽기 편한 구조로 정리했다.
- `service.md` 대비 점검 결과, 주문 목록 조회, 상품 목록 `sort`, 0-based page 기본값, `/api-admin/v1` ADMIN API, `X-Loopers-LoginPw`, 좋아요 목록 경로, 쿠폰 범위가 현재 제출 설계와 맞지 않거나 누락된 후보로 확인됐다.
- 현재 구현 코드는 `ProductV1Controller` 중심의 상품 CRUD만 있으며, 상품 등록/수정/삭제가 `/api/v1/products` 아래에 있어 `service.md`의 ADMIN API 경계와 다르다.
- 내 좋아요 목록 API는 `service.md` 원문을 따라 `GET /api/v1/users/{userId}/likes`로 확정했다.
- 타 유저 직접 접근 금지를 위해 path `userId`와 `X-Loopers-LoginId`가 다르면 요청을 거부하는 정책을 `01-requirements.md`, `02-sequence-diagrams.md`, `.docs/dto-spec.md`에 반영했다.
- 주문 목록 조회는 `service.md` 원문을 따라 `GET /api/v1/orders?startAt&endAt`로 확정했다.
- 주문 목록 조회는 `X-Loopers-LoginId` 기준 본인 주문만 반환하고, `startAt`, `endAt`은 주문 생성 시각 기준 기간 필터로 설계했다.
- 사용자가 남은 선택지는 모두 `service.md` 기준으로 진행하라고 답변했다.
- 반영 범위는 상품 목록 `page=0`, `size=20`, `sort=latest/price_asc/likes_desc`, `/api-admin/v1` ADMIN API, `X-Loopers-LoginId`/`X-Loopers-LoginPw`, `X-Loopers-Ldap`, 쿠폰 확장 포인트 기록이다.
- 사용자가 `.docs/domain.md`에 자주 쓰는 용어 정의를 추가해 달라고 요청했다.
- 상품 목록 페이징/정렬은 `service.md` 기준으로 `page=0`, `size=20`, `sort=latest/price_asc/likes_desc`를 반영했다.
- 대고객 API는 `/api/v1`, ADMIN API는 `/api-admin/v1`로 나누고, ADMIN 헤더는 `X-Loopers-Ldap: loopers.admin`로 반영했다.
- 유저 API 헤더는 `X-Loopers-LoginId`, `X-Loopers-LoginPw`를 모두 받는 것으로 반영했다.
- 브랜드/상품/주문 ADMIN API와 브랜드 삭제 시 브랜드 row 보존 및 상품 판매 중지, 상품 등록 시 기존 활성 브랜드 필수, 상품 수정 시 브랜드 변경 불가 정책을 반영했다.
- 쿠폰은 `service.md`에 구체 API가 없으므로 이번 설계에서는 주문 확장 포인트로만 기록했다.
- 사용자 행동 기록은 좋아요 이력, 주문/주문항목, 주문 완료 outbox를 기준으로 하고 상품 조회 이벤트는 향후 event 확장 포인트로 기록했다.
- `.docs/domain.md`에 자주 쓰는 용어 섹션을 추가했다.
- `.docs/README.md` 문서 지도에 `.codeguide/service.md`를 추가했다.
- PR 리뷰 위험 항목 1번은 ADMIN 상품 삭제와 과거 주문 이력 충돌이었다.
- 사용자는 A안, 즉 상품 삭제를 물리 삭제가 아니라 `ProductStatus.STOPPED` 전환으로 처리하는 soft-delete 정책을 선택했다.
- `DELETE /api-admin/v1/products/{productId}`는 내부적으로 상품 상태를 `STOPPED`으로 전환한다.
- `DELETE /api-admin/v1/brands/{brandId}`도 `product.brand_id` FK 보호를 위해 브랜드 row를 보존하고 `Brand.deletedAt`을 기록하며, 해당 브랜드 상품을 `STOPPED`으로 전환하도록 설계에 반영했다.
- 신규 상품 등록 시에는 `Brand.deletedAt == null`인 활성 브랜드만 허용하도록 시퀀스와 클래스 책임을 맞췄다.
- 이 정책은 과거 주문의 `order_line.product_id`, `product.brand_id` 참조와 상품 스냅샷을 보호하기 위한 결정이다.
- `.docs/design-review.md`에는 PR 리뷰 위험 후보로 `paymentStatus` 표현 방식과 유저 API 시퀀스의 `X-Loopers-LoginPw` 검증 명시를 남겨 두었고, 이 중 `paymentStatus` 표현 방식은 A안으로 처리했다.
- 사용자는 PR 리뷰 위험 항목 2번에 대해 A안, 즉 주문 생성 트랜잭션에서 `payment(order_id, status=REQUESTED)` row를 함께 생성하는 방식을 선택했다.
- 주문 목록/상세 응답의 `paymentStatus`는 nullable이 아니며, 주문 생성 직후에는 `REQUESTED`로 반환하도록 요구사항, 시퀀스, 클래스, ERD, DTO 문서에 반영했다.
- A안에 맞춰 `Order`와 `Payment` 관계도 optional이 아닌 1:1 관계로 정리했다.
- 사용자는 PR 리뷰 위험 항목 3번에 대해 A안, 즉 user_required 시퀀스마다 `X-Loopers-LoginId`와 `X-Loopers-LoginPw` 검증 단계를 명시하는 방식을 선택했다.
- 좋아요 등록/취소, 내 좋아요 목록, 주문 생성, 주문 상태 조회, 주문 목록 조회 시퀀스에 유저 헤더 검증과 실패 분기를 명시했다.
- `.docs/design-review.md`에는 PR 리뷰 위험 후보 3개가 모두 처리된 상태로 기록했다.
- 제출 커밋 `d461cd0`은 `.docs/design/01-requirements.md`, `.docs/design/02-sequence-diagrams.md`, `.docs/design/03-class-diagram.md`, `.docs/design/04-erd.md` 4개 파일만 포함한다.

## 현재 수정 파일

| 파일 | 상태 | 제출 커밋 포함 여부 | 목적 |
| --- | --- | --- | --- |
| `AGENTS.md` | 수정/신규 | 제외 | 작업 규칙, 문서 탐색 순서, 작업 지속성 규칙 |
| `.docs/README.md` | 신규/수정 | 제외 | 문서 지도, `.codeguide/service.md` 탐색 기준 추가 |
| `.docs/design-review.md` | 신규/수정 | 제외 | 설계 누락 항목, 처리 순서, PR 리뷰 위험 항목 3개 처리 현황 |
| `.docs/worklog.md` | 신규/수정 | 제외 | 재개용 현재 작업 상태 |
| `.docs/domain.md` | 신규/수정 | 제외 | 도메인 용어집, 자주 쓰는 용어 정의, 브랜드/상품 soft delete 용어, 결제 요청 row 용어 |
| `.docs/architecture.md` | 신규 | 제외 | 아키텍처 결정 |
| `.docs/test-plan.md` | 신규 | 제외 | 다음 주 구현용 테스트 계획 |
| `.docs/dto-spec.md` | 신규/수정 | 제외 | API DTO 계약, 0-based 페이지 응답, 좋아요 목록 API 경로, 주문 목록 DTO, ADMIN DTO, 브랜드/상품 soft delete 응답, `paymentStatus` non-null 정리 |
| `.docs/design/01-requirements.md` | 수정 | 포함 | `User` 경계, 아키텍처 원칙, API 계약, 상태 전이표, 좋아요 목록 경로, 주문 목록 조회, 페이징/정렬, ADMIN API, 인증 헤더, 브랜드/상품 soft delete, 주문 생성 시 결제 row 생성 반영 |
| `.docs/design/02-sequence-diagrams.md` | 수정 | 포함 | 좋아요 컨트롤러명, Outbox 실패 흐름, 트랜잭션 경계표, user_required 헤더 검증 명시, 좋아요 목록 userId 검증, 주문 목록 조회, 상품 목록 조회, ADMIN soft delete, 주문 생성 시 결제 row 생성 흐름 정리 |
| `.docs/design/03-class-diagram.md` | 수정 | 포함 | 모듈 경계, 도메인 우선 구조, `StockService` 책임, 주문 조회 책임, ADMIN Controller/Service 책임, 현재 구현과 목표 차이, 브랜드/상품 soft delete, `OrderFacade` 결제 row 생성 책임 반영 |
| `.docs/design/04-erd.md` | 수정 | 포함 | `USER` 테이블 상세 제거, `user_id` 참조, Outbox 실패 정책, ADMIN 상품/브랜드 삭제 정책, 브랜드/상품 soft delete와 주문 이력 보호, 주문 생성 시 `Payment(REQUESTED)` row 정리 |
| `.codeguide/loopers-1-week.md` | 수정 | 제외 | 1주차 가이드 AI 친화 정리 |
| `.codeguide/loopers-2-week.md` | 신규 | 제외 | 2주차 가이드 AI 친화 정리 |
| `.codeguide/service.md` | 신규 | 제외 | 서비스 요구사항 가이드 목차, 섹션, 표 구조 정리 |

## 제출 문서 상태

| 파일 | 현재 판단 |
| --- | --- |
| `.docs/design/01-requirements.md` | `service.md` 기준 API 경로, 헤더, 페이징/정렬, ADMIN API, 쿠폰 확장 포인트, 사용자 행동 기록, 브랜드/상품 soft delete, 주문 생성 시 결제 row 생성 반영 완료 |
| `.docs/design/02-sequence-diagrams.md` | 상품 목록, 좋아요, 주문 목록, user_required 헤더 검증, ADMIN soft delete, 주문/결제, 주문 생성 시 결제 row 생성, Outbox 흐름 반영 완료 |
| `.docs/design/03-class-diagram.md` | 도메인 우선 모듈러 모놀리스, `StockService`, ADMIN Controller/Service, 브랜드/상품 soft delete, `OrderFacade` 결제 row 생성 책임, 현재 구현과 목표 차이 반영 완료 |
| `.docs/design/04-erd.md` | `User` 경계, Outbox 상태 정책, ADMIN 상품/브랜드 제약, 브랜드/상품 soft delete로 FK와 주문 이력 보호, `Payment(REQUESTED)` row 생성 시점 반영 완료 |
| `.docs/test-plan.md` | 단위/통합/E2E 테스트 우선순위 분리 완료 |
| `.docs/dto-spec.md` | 공통 페이지 응답, 상품 DTO, `ProductLike` DTO 단일화, `OrderCreateRequest`, `OrderDetailResponse`, `OrderCreateResponse`, `Payment` DTO, `DataPlatformClient` DTO 반영 |

## 다음 작업 순서

1. 작업 후 이 문서를 갱신한다.
2. 다음 재개 시 `AGENTS.md -> .docs/README.md -> .docs/design-review.md -> .docs/worklog.md -> .docs/dto-spec.md` 순서로 확인한다.

## 미확정 질문

- 없음.

## 내일 재개 메모

- `service.md`와 현재 설계의 충돌/누락 후보는 `.docs/design-review.md`에 기록했다.
- 질문 1 답변에 따라 내 좋아요 목록 경로는 `GET /api/v1/users/{userId}/likes`로 반영했다.
- 질문 2 답변에 따라 주문 목록 조회는 `GET /api/v1/orders?startAt&endAt`로 반영했다.
- PR 리뷰 위험 항목 1번은 브랜드/상품 soft delete로 처리 완료했다.
- PR 리뷰 위험 항목 2번은 주문 생성 시 `Payment(REQUESTED)` row를 함께 생성하는 방식으로 처리 완료했다.
- PR 리뷰 위험 항목 3번은 user_required 시퀀스마다 `X-Loopers-LoginId`와 `X-Loopers-LoginPw` 검증 단계를 명시하는 방식으로 처리 완료했다.
- 제출 커밋 `d461cd0` 생성 완료. 다음 작업자는 필요 시 PR 생성 또는 제출만 진행하면 된다.
