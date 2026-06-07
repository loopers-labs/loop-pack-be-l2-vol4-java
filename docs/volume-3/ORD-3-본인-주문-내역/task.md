# Task: ORD-3 본인 주문 내역 (날짜 범위)

**Plan**: ./plan.md

> 완료한 task는 `- [ ]` → `- [X]`. 구현은 CLAUDE.md·컨벤션 준수. 경로 prefix는 `apps/commerce-api/src/{main,test}/java/com/loopers/`. ORD-1 `OrderInfo`·`OrderItemInfo` 재사용.

## Phase 1: 구현 (본인 주문 내역 조회)

- [X] T001 `OrderRepository.findActiveByUserIdAndOrderedAtBetween` 추가 — `main/.../domain/order/OrderRepository.java` (`Page<OrderModel> findActiveByUserIdAndOrderedAtBetween(Long userId, ZonedDateTime start, ZonedDateTime endExclusive, int page, int size)`)
- [X] T002 `OrderJpaRepository` + `OrderRepositoryImpl` 구현 — `main/.../infrastructure/order/OrderJpaRepository.java`(Active+반열린 구간 파생 쿼리 `findByUserIdAndDeletedAtIsNullAndOrderedAtGreaterThanEqualAndOrderedAtLessThan`), `main/.../infrastructure/order/OrderRepositoryImpl.java`(`Sort.by(DESC,"orderedAt")` 위임)
- [X] T003 `OrderRepository` 범위 조회 통합 테스트 — `test/.../infrastructure/order/OrderRepositoryIntegrationTest.java` (범위 안 주문만·주문 시각 내림차순·페이징·총 개수 / 본인 주문만(타 회원 제외) / 삭제 주문 제외). 항목 요약은 E2E에서 검증
- [X] T004 `OrderFacade.readMyOrders` 작성 + 단위 테스트 — `main/.../application/order/OrderFacade.java`(기본값 적용·날짜 범위 검증·반열린 구간 변환), `test/.../application/order/OrderFacadeTest.java` (시작/종료 미지정 시 기본 범위 / 시작>종료 → BAD_REQUEST / 시작>오늘 → BAD_REQUEST). page/size 범위 검증은 프로젝트 컨벤션(클라이언트 신뢰, `query-param-validation`)에 따라 두지 않음 — 기존 목록 cycle과 동일.
- [X] T005 `OrderV1Dto.PageResponse` 추가 — `main/.../interfaces/api/order/OrderV1Dto.java` (`PageResponse(content, page, size, totalElements, totalPages)` + `from(Page<OrderInfo>)`, content는 ORD-1 `OrderResponse`)
- [X] T006 `OrderV1Controller`·`OrderV1ApiSpec`에 내역 조회 추가 — `main/.../interfaces/api/order/OrderV1Controller.java`(`@GetMapping`, `@DateTimeFormat(iso=DATE) LocalDate startAt/endAt required=false`, page/size default, `@LoginUser`), `main/.../interfaces/api/order/OrderV1ApiSpec.java`(`@Operation`)
- [X] T007 E2E 테스트 추가 — `test/.../interfaces/api/OrderV1ApiE2ETest.java` (기본 범위 200+메타 키+항목 키 / 빈 결과 200 / 인증 실패 401 / 시작>종료 400 / 시작>오늘 400. statusCode+meta.result+errorCode). size·page 위반 400은 미검증(컨벤션상 page/size 미검증).

## Phase 2: 마무리

- [X] T008 spec 테스트 계획 대비 누락 점검 (기본값·범위 검증·양끝 포함·정렬·본인 격리·페이징·항목 요약 매핑)
- [X] T009 `.http` 파일에 내역 조회 샘플 추가 — `http/commerce-api/order-v1.http` (범위 지정 / 범위 미지정(기본) / 시작>종료(400))
