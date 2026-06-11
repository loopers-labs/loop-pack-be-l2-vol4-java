# 4주차 트랜잭션 자가 분석 — analyze-query 스킬 적용 결과

> 분석 도구: `.claude/skills/analyze-query/SKILL.md` / 분석일: 2026-06-11
> 대상: `OrderFacade.createOrder()`, `CouponFacade`, `LikeFacade`, `ProductFacade.getProducts()`

## 1. 분석 대상 / 범위

4주차에서 트랜잭션·락이 추가된 유스케이스 전체. 컨트롤러 → Facade → 도메인 서비스 → 레포지토리 흐름 기준.

## 2. 트랜잭션 경계 — OrderFacade.createOrder()

```
@Transactional createOrder()
  ├─ 유저 검증 (loginId → User)            락 없음
  ├─ 쿠폰 조회 (couponId 있을 때)           락 없음   ← [개선 1] 적용: 락 이전으로 이동
  ├─ 상품 락 배치 조회 (FOR UPDATE, id 정렬) ★ 락 보유 시작
  ├─ OrderService.place()                  메모리 연산 (재고 차감, 쿠폰 use, 할인 계산, 조립)
  ├─ product/userCoupon/order save
  └─ 커밋 — flush 시 UPDATE/INSERT 실행,    ★ 락 해제
            user_coupon 은 @Version 검증
```

- 트랜잭션이 필요한 핵심 작업: 재고 차감, 쿠폰 사용, 주문 저장 — 셋의 원자성이 과제 요구사항이므로 단일 트랜잭션이 맞다.
- 외부 API 호출: 없음 ✅ (향후 PG 연동이 들어오면 트랜잭션 분리 필요 — 백로그)
- 락 보유 구간에는 메모리 연산과 저장만 존재 ✅

## 3. 체크리스트 점검 결과

### ① Transaction Boundary

| 항목 | 결과 |
| --- | --- |
| 트랜잭션 시작 지점 | 전부 Facade 메서드 단위 ✅ (Controller @Transactional 없음) |
| 외부 호출 포함 여부 | 없음 ✅ |
| 락 보유 구간 최소화 | **[개선 1] 발견·적용** — 아래 참조 |

### ② 불필요하게 큰 트랜잭션

- 조회 메서드는 전부 `@Transactional(readOnly = true)` ✅ (getOrder, getMyCoupons, getCoupons, getProducts 등)
- 쓰기 트랜잭션에 대량 조회 혼입 없음 ✅

### ③ JPA / 영속성 컨텍스트

| 점검 | 결과 |
| --- | --- |
| flush 시점 | 명시 save + 커밋 시 flush. 레포지토리 패턴 컨벤션 유지 (managed 엔티티의 save 는 사실상 no-op — 의도적 수용) |
| 조건부 UPDATE 와 1차 캐시 | `product_like_count` 의 네이티브 upsert 는 영속성 컨텍스트를 우회하지만, **같은 트랜잭션에서 해당 엔티티를 로드하는 경로가 없음**(쓰기는 upsert 만, 읽기는 별도 readOnly 트랜잭션) ✅ |
| N+1 | 상품 목록의 좋아요 수: `findAllByProductIds` 배치(IN) 조회 ✅. 주문 items 는 EAGER + 건별 INSERT — 주문당 항목 수가 작아 수용, 대량화 시 `hibernate.jdbc.batch_size` 백로그 |
| 쿠폰 스냅샷 | 주문 트랜잭션에서 쿠폰 템플릿(coupon 테이블) 조회 **0회** ✅ — CouponSnapshot 자기완결 설계의 효과 |

### ④ 발견사항

**[개선 1 — 적용됨] 쿠폰 조회가 상품 락 보유 중에 수행**
- 문제: `FOR UPDATE` 획득 후 user_coupon SELECT → 락 보유 시간 증가, 무효 쿠폰이어도 락을 잡았다 버림. 인기 상품일수록 뒤에 줄 선 주문들이 그만큼 더 대기.
- 조치: 쿠폰 조회를 상품 락 획득 이전으로 이동. 쿠폰 검증(use)·@Version 보장은 변함없이 트랜잭션 내에서 수행.
- 검증: 전체 테스트 그린 유지 (실패 순서 변화는 E2E 기대값에 영향 없음 — 쿠폰 미존재 404 동일).

**[발견 2 — 결정 완료·적용] LikeFacade 의 멱등 체크 race → 409 매핑**
- `existsBy` 확인과 `save` 사이에 같은 유저의 동시 요청(더블클릭)이 끼어들면 둘 다 통과 → 한쪽이 unique 제약 위반 `DataIntegrityViolationException` → 기존엔 500 응답.
- 정합성은 unique 제약이 지켜주지만(중복 행 불가, 카운트도 한쪽 롤백) 응답 경험만 500.
- **결정(개발자)**: 더블클릭은 제약 위반으로 실패 처리 — `ApiControllerAdvice` 에서 `DataIntegrityViolationException` → **409 CONFLICT** 매핑.
- 참고: "catch 해서 멱등 성공 흡수" 안은 기각 — 제약 위반이 flush 시점에 발생하면 Hibernate 가 트랜잭션을 rollback-only 로 마킹해, 예외를 잡아도 커밋이 `UnexpectedRollbackException` 으로 실패한다(JPA 의 구조적 한계). REQUIRES_NEW 분리까지 갈 가치는 없는 희귀 시나리오.
- 검증: `LikeConcurrencyTest` 에 동일 유저 20 동시 요청 테스트 추가 — like 행 1개, 카운트 1 보장.

**[수용 — 트레이드오프 기록] 명시 save 호출**
- managed 엔티티는 dirty checking 만으로 UPDATE 되므로 `productRepository.save()` 는 기술적으로 불필요하나, "쓰기는 레포지토리를 통한다"는 프로젝트 컨벤션의 가독성 이득이 더 크다고 판단해 유지.

## 4. 결론

- 트랜잭션 경계는 전부 Facade 메서드 단위로 일관, readOnly 분리 완료, 외부 호출 없음.
- 락 전략 3분할(비관/낙관/조건부 UPDATE)이 의도대로 동작함을 동시성 테스트 3종이 보증.
- 개선 1건 적용(락 보유 시간 단축), 발견 1건은 결정 대기, 백로그 2건(INSERT 배치, PG 연동 시 트랜잭션 분리) 기록.
