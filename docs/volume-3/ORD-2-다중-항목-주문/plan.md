# Plan: ORD-2 다중 항목 주문

**Spec**: ./spec.md
**작성일**: 2026-05-27

## 요약

ORD-1이 세운 `POST /api/v1/orders` 파이프라인을 다중 항목으로 일반화한다. 신규 파일은 없고 `OrderFacade.createOrder`에 두 규칙만 추가한다: ① **같은 상품 식별자 중복 거부**(distinct productId 수 < 항목 수 → BAD_REQUEST), ② **상품 식별자 오름차순 정렬 후 차감**(교착 회피, 결정 4). 전부 성공 또는 전부 실패는 ORD-1의 `@Transactional`이 이미 보장(어느 항목의 NOT_FOUND·CONFLICT가 던져지면 이미 차감된 항목까지 롤백). 요청 본문(`items` 리스트)·응답·도메인 모델은 ORD-1 그대로 재사용한다.

## 기술 컨텍스트

- 고정: Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api
- 시나리오별 추가: 없음 (ORD-1 골격 재사용)

## 컨벤션·결정 점검

- [x] 호출 방향 준수 — ORD-1 `OrderFacade`에 로직만 추가
- [x] 검증: 수량은 `Quantity.from()` 단일 원천(ORD-1). 빈 목록은 DTO `@NotEmpty`(ORD-1). 중복 productId 거부는 Facade 책임(입력 정합성 규칙이라 도메인 VO 아님)
- [x] 결정 4(교착 회피): 항목을 productId 오름차순 정렬 후 정렬 순서대로 차감 → 동시 요청의 잠금 획득 순서 일관
- [x] 결정 4(음수 방지): 항목마다 ORD-1의 원자적 조건부 차감 재사용
- [x] 전부 성공/전부 실패: `@Transactional` 롤백으로 보장(부분 성공 정책 없음)
- [x] 결정 5(스냅샷): 항목별 스냅샷 — ORD-1 로직이 항목 순회라 그대로 적용

## 레이어별 설계 결정 & 파일 맵

### interfaces
- 변경 없음 — `OrderV1Controller`·`OrderV1Dto`·`OrderV1ApiSpec`(ORD-1)이 이미 `items` 리스트를 받는다.

### application
- `application/order/OrderFacade.java` (편집) — `createOrder`에 추가:
  - **중복 검사**: `items`의 productId distinct 수가 항목 수보다 적으면 `CoreException(BAD_REQUEST, "같은 상품은 한 번만 담을 수 있습니다.")`.
  - **정렬**: 차감 루프 전에 `items`를 productId 오름차순 정렬.
  - 나머지(상품 활성 조회·브랜드명·원자 차감·스냅샷·저장)는 ORD-1과 동일하게 항목 순회. 총액은 Facade가 항목 합으로 계산(ORD-1).

### domain
- 변경 없음 — `OrderModel`은 items를 들지 않고, Facade가 항목 리스트를 순회·`save(order, items)`로 저장하므로 다중 항목도 ORD-1과 동일 경로. N개 처리에 모델 변경 불필요.

### infrastructure
- 변경 없음 — 원자 차감 쿼리(ORD-1)를 항목 수만큼 호출.

## 복잡도 트래킹

| 결정 | 이유 | 기각한 더 단순한 대안 |
|------|------|----------------------|
| 중복 productId 거부를 Facade에서(도메인 VO 아님) | "한 주문 안 같은 상품 단일 줄"은 항목 집합의 입력 정합성 규칙이라 개별 VO의 책임 밖. 합산 책임은 클라이언트(결정) | 도메인 모델이 중복 병합/거부(서버가 합산 책임을 떠안음 — 정책 위반) |
| 정렬 키를 productId 오름차순으로 | 결정 4의 교착 회피 = 모든 요청이 같은 순서로 잠금 획득. productId는 안정적·전순서 키 | 정렬 없이 요청 순서대로 차감(두 요청이 반대 순서면 교착 가능) |
| ORD-1과 동일 유스케이스에 규칙만 추가(신규 파일 0) | ORD-2는 "항목 수 1 이상 일반화"라 ORD-1 파이프라인의 상위집합. 별도 엔드포인트·DTO·모델 불필요 | 다중 전용 컨트롤러/DTO 분리(같은 API 공유 요구사항 위반·중복) |
