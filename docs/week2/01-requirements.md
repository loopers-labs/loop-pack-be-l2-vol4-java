# 감성 이커머스 서비스 요구사항 명세 (Week 2)

## 1. 개요
본 문서는 '감성 이커머스' 서비스의 핵심 기능인 유저 관리, 브랜드 및 상품 관리, 그리고 주문 시스템에 대한 요구사항을 정의합니다. 
단순한 기능 구현을 넘어, 데이터의 일관성과 이력 보존을 고려한 설계를 지향합니다.

---

## 2. 핵심 비즈니스 정책

### 2.1 재고 관리 및 결제 정책
*   **재고 차감 시점:** 결제 완료 시점에 차감을 원칙으로 하나, 현재 단계에서는 **주문 생성 시 즉시 결제 완료로 간주하여 재고를 차감**한다.
*   **재고 부족 처리:** 주문 요청 수량이 잔여 재고보다 많을 경우, 해당 주문 건 전체를 실패(Rollback) 처리하고 적절한 에러 메시지를 반환한다.
*   **동시성 제어:** 다수의 유저가 동일 상품을 동시에 주문할 경우, 재고 수량의 정합성이 깨지지 않도록 동시성 제어 메커니즘을 적용해야 한다.

### 2.2 주문 데이터 불변성 (Snapshot)
*   **스냅샷 전략:** 주문이 완료된 시점의 상품 정보를 `ORDER_ITEM` 테이블에 직접 복제하여 저장한다.
*   **보관 필드:** 상품명(`name`), 가격(`price`), 브랜드명(`brand_name`) 등 핵심 정보를 컬럼 단위로 저장하여, 원본 상품 정보가 변경되거나 삭제되어도 주문 당시의 정보를 보존한다.

### 2.3 데이터 삭제 정책 (Soft Delete)
*   **논리 삭제 적용:** 브랜드 및 상품 삭제 시 DB에서 레코드를 물리적으로 제거하지 않고, `is_deleted`와 같은 플래그를 사용하여 논리적으로 삭제 처리한다.
*   **연쇄 삭제:** 브랜드 삭제 시, 해당 브랜드에 속한 모든 상품도 함께 논리 삭제 처리되어야 한다.

### 2.4 사용자 식별 및 보안
*   **인증/인가:** 별도의 인증 프레임워크를 사용하지 않으며, 아래의 HTTP 헤더를 통해 사용자를 식별한다.
    *   **일반 유저:** `X-Loopers-LoginId` (아이디), `X-Loopers-LoginPw` (비밀번호)
    *   **어드민 유저:** `X-Loopers-Ldap` (값: `loopers.admin`)
*   **접근 제어:** 유저는 본인의 정보 및 주문 내역만 조회/수정할 수 있으며, 타인의 데이터에 직접 접근할 수 없다.

### 2.5 좋아요(Likes) 및 정렬 정책
*   **좋아요 수 관리 (Denormalization):** 상품 목록의 '좋아요 많은 순' 정렬 성능을 위해 `PRODUCT` 테이블에 `like_count` 컬럼을 유지하며, 좋아요 등록/취소 시 이를 실시간으로 업데이트한다.
*   **멱등성 보장:** 이미 좋아요를 누른 상품에 대해 중복 요청이 올 경우, 에러를 발생시키지 않고 성공(200 OK)을 반환하여 클라이언트의 예외 처리를 단순화한다.
*   **삭제 데이터 처리:**
    *   상품이나 브랜드가 논리 삭제된 경우, '나의 좋아요 목록'에서 자동으로 제외한다.
    *   이미 삭제된 상품에 대해 좋아요를 등록하려고 시도할 경우 에러를 반환한다.

---

## 3. 기능 요구사항 (API List)

### 3.1 유저 (Users)
| METHOD | URI | user_required | 설명 |
| --- | --- | --- | --- |
| POST | `/api/v1/users` | X | 회원가입 |
| GET | `/api/v1/users/me` | O | 내 정보 조회 |
| PUT | `/api/v1/users/password` | O | 비밀번호 변경 |

### 3.2 브랜드 & 상품 (Brands / Products)
#### 고객용 기능
| METHOD | URI | user_required | 설명 |
| --- | --- | --- | --- |
| GET | `/api/v1/brands/{brandId}` | X | 브랜드 정보 조회 |
| GET | `/api/v1/products` | X | 상품 목록 조회 (필터 및 정렬 지원) |
| GET | `/api/v1/products/{productId}` | X | 상품 상세 정보 조회 |

*   **상품 목록 조회 파라미터:**
    *   `brandId`: 특정 브랜드 필터링
    *   `sort`: `latest` (기본), `price_asc`, `likes_desc`
    *   `page`, `size`: 페이징 지원 (기본 0, 20)

#### 어드민 기능
| METHOD | URI | ldap_required | 설명 |
| --- | --- | --- | --- |
| GET | `/api-admin/v1/brands` | O | 등록된 브랜드 목록 조회 |
| POST | `/api-admin/v1/brands` | O | 브랜드 등록 |
| PUT | `/api-admin/v1/brands/{brandId}` | O | 브랜드 정보 수정 |
| DELETE | `/api-admin/v1/brands/{brandId}` | O | 브랜드 삭제 (연관 상품 논리 삭제 포함) |
| GET | `/api-admin/v1/products` | O | 등록된 상품 목록 조회 (brandId 필터 포함) |
| POST | `/api-admin/v1/products` | O | 상품 등록 (유효한 브랜드 ID 필수) |
| PUT | `/api-admin/v1/products/{productId}` | O | 상품 정보 수정 (브랜드 변경 불가) |
| DELETE | `/api-admin/v1/products/{productId}` | O | 상품 삭제 (논리 삭제) |

### 3.3 좋아요 (Likes)
| METHOD | URI | user_required | 설명 |
| --- | --- | --- | --- |
| POST | `/api/v1/products/{productId}/likes` | O | 상품 좋아요 등록 (중복 요청 시 성공 반환) |
| DELETE | `/api/v1/products/{productId}/likes` | O | 상품 좋아요 취소 |
| GET | `/api/v1/users/me/likes` | O | 내가 좋아요 한 상품 목록 조회 (삭제된 상품 제외) |

### 3.4 주문 (Orders)
#### 고객용 기능
| METHOD | URI | user_required | 설명 |
| --- | --- | --- | --- |
| POST | `/api/v1/orders` | O | 주문 요청 (재고 차감 및 스냅샷 저장 포함) |
| GET | `/api/v1/orders` | O | 유저의 주문 목록 조회 (`startAt`, `endAt` 기간 필터) |
| GET | `/api/v1/orders/{orderId}` | O | 단일 주문 상세 조회 |

#### 어드민 기능
| METHOD | URI | ldap_required | 설명 |
| --- | --- | --- | --- |
| GET | `/api-admin/v1/orders` | O | 전체 주문 목록 조회 |
| GET | `/api-admin/v1/orders/{orderId}` | O | 단일 주문 상세 조회 |

---

## 4. 기술적 제약 사항 및 고려 사항
- **데이터 정합성:** 재고 확인 및 차감은 원자적(Atomic)으로 수행되어야 한다.
- **예외 처리:** 재고 부족, 권한 위반, 유효하지 않은 브랜드 참조 등에 대해 적절한 에러 응답을 반환한다.
- **확장성:** 추후 결제 단계 분리 및 쿠폰 기능 도입을 고려한 유연한 도메인 모델을 구축한다.
- **삭제 정책:** 모든 삭제는 `Logical Delete`를 원칙으로 하여 주문 이력과의 참조 무결성을 유지한다.
