# 유비쿼터스 언어 (Ubiquitous Language)

모든 협업자가 동일한 언어로 도메인을 이해하고 소통하기 위한 공통 용어 정의서.
코드, 문서, 대화에서 아래 용어를 일관되게 사용한다.

---

## 1. 액터 (Actors)

| 한글 | 영어 | 설명 |
|------|------|------|
| 사용자 / 유저 | User | 서비스에 가입하여 로그인한 일반 사용자 |
| 비회원 | Guest | 로그인하지 않은 방문자. 상품·브랜드 조회만 가능 |
| 어드민 | Admin | 서비스 내부 관리자. `/api-admin/v1` 경로로 접근하며 별도 권한 확인이 필요 |

---

## 2. 핵심 도메인 엔티티 (Core Domain Entities)

| 한글 | 영어 | 설명 |
|------|------|------|
| 유저 | User | 서비스 회원. `loginId`로 식별 |
| 브랜드 | Brand | 상품을 등록하는 판매 주체 단위. 이름은 중복 불가 |
| 상품 | Product | 브랜드에 속하는 판매 단위. 가격·재고·카테고리를 가짐 |
| 좋아요 | Like | 유저가 특정 상품에 관심을 표시한 기록. 상품당 1개만 허용 |
| 주문 | Order | 유저가 장바구니에서 선택한 상품들로 구성된 구매 요청 |
| 주문 항목 | OrderItem | 주문에 포함된 개별 상품과 수량 |
| 장바구니 | Cart | 유저가 구매 전 상품을 임시로 보관하는 공간 |
| 장바구니 항목 | CartItem | 장바구니에 담긴 개별 상품과 수량 |
| 결제 | Payment | 주문에 대한 실제 대금 지불 처리 |
| 결제 취소 | PaymentCancel | 완료된 결제를 취소하는 처리. 별도 이력으로 기록 |
| 쿠폰 | Coupon | 결제 시 할인에 사용할 수 있는 혜택 수단 |
| 수익 | Revenue | 어드민이 조회하는 기간별 서비스 수익. 순 수익 = 총 결제액 - 총 할인액 |

---

## 3. 주요 도메인 개념 (Domain Concepts)

| 한글 | 영어 | 설명 |
|------|------|------|
| 상품 스냅샷 | ProductSnapshot | 주문 시점에 저장되는 상품 정보의 사본 (이름, 가격 등). 이후 상품 정보가 변경되어도 주문 당시 내역을 보존 |
| 재고 | Stock | 상품의 현재 판매 가능 수량 |
| 좋아요 수 | LikeCount | 상품에 누적된 좋아요 개수. 상품 정보에 반영되어 정렬 기준으로 사용 |
| 총 결제액 | TotalPaymentAmount | 기간 내 결제 완료된 주문들의 결제 금액 합산 |
| 총 할인액 | TotalDiscountAmount | 기간 내 쿠폰 등으로 할인된 금액 합산 |
| 순 수익 | NetRevenue | 총 결제액 - 총 할인액으로 산출하는 실제 수익 |

---

## 4. 주문 상태 (Order Status)

| 한글 | 영어 (코드값) | 설명 |
|------|--------------|------|
| 결제 완료 | PAID | 주문 생성과 동시에 결제가 완료된 상태 |
| 취소 | CANCELLED | 주문이 취소된 상태 |

---

## 5. 쿠폰 상태 (Coupon Status)

| 한글 | 영어 (코드값) | 설명 |
|------|--------------|------|
| 사용 가능 | AVAILABLE | 결제에 적용할 수 있는 상태 |
| 사용 완료 | USED | 결제에 이미 사용된 상태. 재사용 불가 |

---

## 6. 핵심 도메인 행위 (Domain Actions)

| 한글 | 영어 | 발생 시점 | 설명 |
|------|------|-----------|------|
| 재고 차감 | DecreaseStock | 주문 생성 시 | 주문 상품의 수량만큼 재고를 줄임 |
| 재고 복원 | RestoreStock | 결제 취소 시 | 차감되었던 재고를 원복 |
| 쿠폰 소진 | MarkCouponAsUsed | 결제 완료 시 | 사용된 쿠폰을 USED 상태로 전환 |
| 쿠폰 복원 | RestoreCoupon | 결제 취소 시 | 소진된 쿠폰을 다시 AVAILABLE 상태로 전환 |
| 장바구니 삭제 | DeleteCartItems | 주문 생성 시 | 주문으로 전환된 상품을 장바구니에서 제거 |
| 장바구니 복원 | RestoreCartItems | 결제 취소 시 | 취소된 주문 상품을 장바구니에 다시 담음 |
| 좋아요 수 증가 | IncreaseLikeCount | 좋아요 등록 시 | 상품의 LikeCount를 1 증가 |
| 좋아요 수 감소 | DecreaseLikeCount | 좋아요 취소 시 | 상품의 LikeCount를 1 감소 |

---

## 7. 조회 조건 및 정렬 (Query Conditions & Sort)

| 한글 | 영어 (파라미터 / 코드값) | 설명 |
|------|--------------------------|------|
| 상품명 검색 | `productName` (like) | 상품명 부분 일치 검색 |
| 브랜드명 검색 | `brandName` (like) | 브랜드명 부분 일치 검색 |
| 대분류 | `categoryLarge` | 카테고리 최상위 분류 |
| 중분류 | `categoryMiddle` | 카테고리 중간 분류. 대분류가 있어야 사용 가능 |
| 소분류 | `categorySmall` | 카테고리 최하위 분류. 중분류가 있어야 사용 가능 |
| 최신순 | `sort=latest` | 등록일 기준 내림차순 (기본값) |
| 가격 오름차순 | `sort=price_asc` | 가격 낮은 순 |
| 좋아요 많은 순 | `sort=likes_desc` | LikeCount 기준 내림차순 |
| 페이지네이션 | Pagination | `page`(페이지 번호, 기본 0) + `size`(건수, 기본 20) 조합 |

---

## 8. 식별자 (Identifiers)

| 한글 | 영어 | 설명 |
|------|------|------|
| 로그인 ID | loginId | 유저를 식별하는 고유 값. 헤더 `X-Loopers-LoginId`로 전달 |
| 유저 ID | userId | DB 상 유저의 고유 식별자 |
| 브랜드 ID | brandId | 브랜드의 고유 식별자 |
| 상품 ID | productId | 상품의 고유 식별자 |
| 주문 ID | orderId | 주문의 고유 식별자 |
| 결제 ID | paymentId | 결제 건의 고유 식별자 |
| 쿠폰 ID | couponId | 쿠폰의 고유 식별자 |
| 장바구니 항목 ID | cartItemId | 장바구니 개별 항목의 고유 식별자 |

---

## 9. 아키텍처 컴포넌트 (Architecture Components)

코드와 다이어그램에서 사용하는 레이어 명칭.

| 명칭 | 역할 |
|------|------|
| Controller | 외부 HTTP 요청을 받아 응답을 반환하는 진입점. 비즈니스 로직을 포함하지 않음 |
| Facade | 여러 도메인 서비스를 조율하는 중간 계층. 크로스 도메인 호출이 발생할 때 Controller와 Service 사이에 위치 |
| Service | 도메인 비즈니스 로직을 담당하는 계층. 단일 도메인 내의 규칙을 처리 |
| Repository | 데이터 영속성을 담당하는 계층. DB 접근을 추상화 |
| Reader | 특정 도메인 엔티티의 읽기 전용 조회를 담당하는 컴포넌트. 타 도메인 Service가 직접 Repository에 접근하는 것을 방지 |

---

## 10. API 경로 규칙 (API Path Convention)

| 구분 | Prefix | 인증 방식 |
|------|--------|-----------|
| 대고객 API | `/api/v1` | 헤더 `X-Loopers-LoginId` / `X-Loopers-LoginPw` |
| 어드민 API | `/api-admin/v1` | 헤더 `X-Loopers-Ldap: loopers.admin` |
