# 시퀀스 다이어그램

## 액터 정의

| 액터 | 설명 |
|---|---|
| `User` | 로그인한 회원 |
| `Customer` | 비로그인 포함 고객 (상품 조회 등 인증 불필요 기능) |
| `Admin` | 내부 관리자 (`X-Loopers-Ldap` 헤더 인증) |
| `DB` | MySQL (영속성 저장소) |
| `Streamer` | Commerce Streamer — Outbox 폴링 후 외부 시스템 연동 |

---

## 1. 주문 생성 (ORDER-001) — 동시성 제어

```mermaid
sequenceDiagram
  actor User as User
  participant OC as OrderController
  participant OF as OrderFacade
  participant PS as ProductService
  participant CS as CouponService
  participant OS as OrderService
  participant DB as DB
  participant Streamer as Streamer

  User->>OC: 주문 요청 (items, couponId?)
  OC->>OC: 유저 인증

  alt 인증 실패
    OC-->>User: 401 Unauthorized
  else 인증 성공
    OC->>OF: 주문 생성 위임
    Note over OF,DB: 트랜잭션 시작

    loop 상품별 처리 (productId 오름차순 — 데드락 방지)
      OF->>PS: 상품 조회 및 재고 차감 요청
      PS->>DB: 상품 존재 여부 확인

      alt 상품 없음 또는 삭제됨
        Note over OF,DB: 트랜잭션 롤백
        OF-->>OC: 예외 발생
        OC-->>User: 404 Not Found
      else 상품 유효
        PS->>DB: 재고 확인 (SELECT FOR UPDATE — 동시 접근 차단)
        Note over DB: 동시 요청은 재고 확인 완료까지 대기

        alt 재고 부족
          Note over OF,DB: 트랜잭션 롤백
          OF-->>OC: 예외 발생
          OC-->>User: 409 Conflict (재고 부족)
        else 재고 충분
          PS->>DB: 재고 차감
          PS-->>OF: 차감 완료
        end
      end
    end

    opt couponId 제공됨 — 총 주문 금액 확정 후 쿠폰 적용
      OF->>CS: 쿠폰 유효성 검증 및 사용 처리
      CS->>DB: 발급 쿠폰 조회 (소유자·상태·만료일 확인)

      alt 쿠폰 없음
        Note over OF,DB: 트랜잭션 롤백
        OF-->>OC: 예외 발생
        OC-->>User: 404 Not Found
      else 소유자 불일치
        Note over OF,DB: 트랜잭션 롤백
        OF-->>OC: 예외 발생
        OC-->>User: 403 Forbidden
      else 사용 불가 (USED / EXPIRED / 최소금액 미충족)
        Note over OF,DB: 트랜잭션 롤백
        OF-->>OC: 예외 발생
        OC-->>User: 409 Conflict
      else 쿠폰 유효
        CS->>DB: 쿠폰 상태 USED 로 변경
        CS-->>OF: 할인 금액 반환
      end
    end

    OF->>OS: 주문 생성 요청 (originalPrice, discountAmount, totalPrice)
    OS->>DB: 주문 생성 (결제 대기 상태, 금액 스냅샷 포함)
    OS->>DB: 주문 상품 저장 (주문 시점 상품명·가격 스냅샷)
    OS->>DB: 외부 연동 이벤트 등록
    OS-->>OF: 생성된 주문 반환
    Note over OF,DB: 트랜잭션 커밋

    OF-->>OC: 주문 결과 반환
    OC-->>User: 201 Created (orderId)
  end

  Note over Streamer,DB: 별도 스케줄 — 외부 시스템 연동
  Streamer->>DB: 미처리 이벤트 조회
  Streamer->>Streamer: 외부 시스템 연동 처리
  Streamer->>DB: 이벤트 처리 완료로 상태 변경
```

---

## 2. 쿠폰 발급 (COUPON-001)

```mermaid
sequenceDiagram
  actor User as User
  participant CC as CouponController
  participant CS as CouponService
  participant DB as DB

  User->>CC: 쿠폰 발급 요청 (couponId)
  CC->>CC: 유저 인증

  alt 인증 실패
    CC-->>User: 401 Unauthorized
  else 인증 성공
    CC->>CS: 쿠폰 발급 요청
    CS->>DB: 쿠폰 존재 여부 확인

    alt 쿠폰 없음 또는 삭제됨
      CS-->>CC: 예외 발생
      CC-->>User: 404 Not Found
    else 쿠폰 유효
      CS->>DB: IssuedCoupon 생성 (AVAILABLE, expiredAt 스냅샷 포함)
      CC-->>User: 201 Created
    end
  end
```

---

## 3. 내 쿠폰 목록 조회 (COUPON-002)

```mermaid
sequenceDiagram
  actor User as User
  participant CC as CouponController
  participant CS as CouponService
  participant DB as DB

  User->>CC: 내 쿠폰 목록 조회 요청
  CC->>CC: 유저 인증

  alt 인증 실패
    CC-->>User: 401 Unauthorized
  else 인증 성공
    CC->>CS: 쿠폰 목록 조회 요청
    CS->>DB: 유저의 IssuedCoupon 목록 조회
    CS->>CS: expiredAt 기준으로 EXPIRED 상태 판별
    CS-->>CC: 쿠폰 목록 반환 (AVAILABLE / USED / EXPIRED)
    CC-->>User: 200 OK (쿠폰 목록)
  end
```

---

## 4. 상품 좋아요 등록 / 취소 (LIKE-001, LIKE-002) — 멱등성 보장

```mermaid
sequenceDiagram
  actor User as User
  participant LC as LikeController
  participant LS as LikeService
  participant DB as DB

  Note over User,DB: 좋아요 등록 (POST /api/v1/products/{productId}/likes)

  User->>LC: 좋아요 등록 요청
  LC->>LC: 유저 인증

  alt 인증 실패
    LC-->>User: 401 Unauthorized
  else 인증 성공
    LC->>LS: 좋아요 등록 요청
    LS->>DB: 상품 존재 여부 확인

    alt 상품 없음 또는 삭제됨
      LS-->>LC: 예외 발생
      LC-->>User: 404 Not Found
    else 상품 유효
      LS->>DB: 이미 좋아요 했는지 확인

      alt 이미 좋아요 존재 (중복 요청)
        LC-->>User: 200 OK (무시)
      else 좋아요 없음
        LS->>DB: 좋아요 저장
        LC-->>User: 200 OK
      end
    end
  end

  Note over User,DB: 좋아요 취소 (DELETE /api/v1/products/{productId}/likes)

  User->>LC: 좋아요 취소 요청
  LC->>LC: 유저 인증

  alt 인증 실패
    LC-->>User: 401 Unauthorized
  else 인증 성공
    LC->>LS: 좋아요 취소 요청
    LS->>DB: 상품 존재 여부 확인

    alt 상품 없음 또는 삭제됨
      LS-->>LC: 예외 발생
      LC-->>User: 404 Not Found
    else 상품 유효
      LS->>DB: 좋아요 여부 확인

      alt 좋아요 없음 (이미 취소된 상태)
        LC-->>User: 200 OK (무시)
      else 좋아요 존재
        LS->>DB: 좋아요 삭제
        LC-->>User: 200 OK
      end
    end
  end
```

---

## 5. 상품 목록 조회 (PRODUCT-001) — 필터 / 정렬 / 페이지네이션

```mermaid
sequenceDiagram
  actor Customer as Customer
  participant PC as ProductController
  participant PS as ProductService
  participant DB as DB

  Customer->>PC: 상품 목록 조회 요청 (브랜드·정렬·페이지 조건)
  PC->>PC: 요청 파라미터 유효성 검증

  alt 유효하지 않은 파라미터
    PC-->>Customer: 400 Bad Request
  else 파라미터 유효
    PC->>PS: 상품 목록 조회 (브랜드·정렬·페이지 조건 적용)

    opt 브랜드 필터 존재
      PS->>DB: 브랜드 존재 여부 확인

      alt 브랜드 없음
        PS-->>PC: 예외 발생
        PC-->>Customer: 404 Not Found
      end
    end

    PS->>DB: 조건에 맞는 상품 목록 조회
    PS-->>PC: 상품 목록 반환
    PC-->>Customer: 200 OK (상품 목록 + 페이지 정보)
  end
```

---

## 6. 브랜드 삭제 (BRAND-ADMIN-005) — Soft Delete Cascade

```mermaid
sequenceDiagram
  actor Admin as Admin
  participant BC as BrandController
  participant BS as BrandService
  participant PS as ProductService
  participant DB as DB

  Admin->>BC: 브랜드 삭제 요청
  BC->>BC: 어드민 인증 (X-Loopers-Ldap)

  alt 인증 실패
    BC-->>Admin: 401 Unauthorized
  else 인증 성공
    BC->>BS: 브랜드 삭제 요청
    BS->>DB: 브랜드 존재 여부 확인

    alt 브랜드 없음
      BS-->>BC: 예외 발생
      BC-->>Admin: 404 Not Found
    else 브랜드 존재
      Note over BS,DB: 트랜잭션 시작
      BS->>PS: 소속 상품 전체 삭제 요청
      PS->>DB: 소속 상품 전체 삭제 처리
      PS-->>BS: 완료
      BS->>DB: 브랜드 삭제 처리
      Note over BS,DB: 트랜잭션 커밋
      BC-->>Admin: 200 OK
    end
  end
```
