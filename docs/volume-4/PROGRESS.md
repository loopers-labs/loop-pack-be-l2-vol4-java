# 구현 진행 보드

소스: `docs/volume-4/01-requirements.md` ~ `04-erd.md` (+ `.private/volume-4/quests/requirements.md` 원문)
출력: `docs/volume-4/` (시나리오별 `<ID>-<제목>/` 폴더에 spec·plan·task 산출물)
현재: CPN-1 / review

단계 = `spec → plan → task → analyze → implement → test → review → commit` 중 현재 위치(시작 전이면 `-`).
상태 = ⬜ todo / 🔄 진행 / ✅ done.

| # | ID | 제목 | 도메인 | API | 의존성 | 단계 | 상태 |
|---|----|------|--------|-----|--------|------|------|
| 1 | CPN-1 | 쿠폰 템플릿 등록 | Coupon | `POST /api-admin/v1/coupons` | admin 인증(기존) | review | 🔄 진행 |
| 2 | CPN-2 | 쿠폰 템플릿 수정 | Coupon | `PUT /api-admin/v1/coupons/{couponId}` | CPN-1 | - | ⬜ todo |
| 3 | CPN-3 | 쿠폰 템플릿 삭제 | Coupon | `DELETE /api-admin/v1/coupons/{couponId}` | CPN-1 | - | ⬜ todo |
| 4 | CPN-4 | 쿠폰 템플릿 목록 | Coupon | `GET /api-admin/v1/coupons` | CPN-1 | - | ⬜ todo |
| 5 | CPN-5 | 쿠폰 템플릿 상세 | Coupon | `GET /api-admin/v1/coupons/{couponId}` | CPN-1 | - | ⬜ todo |
| 6 | CPN-6 | 쿠폰 발급 | Coupon | `POST /api/v1/coupons/{couponId}/issue` | CPN-1, User | - | ⬜ todo |
| 7 | CPN-7 | 내 쿠폰 목록 (상태 포함) | Coupon | `GET /api/v1/users/me/coupons` | CPN-6 | - | ⬜ todo |
| 8 | CPN-8 | 쿠폰 발급 내역 조회 | Coupon | `GET /api-admin/v1/coupons/{couponId}/issues` | CPN-6 | - | ⬜ todo |
| 9 | ORD-7 | 주문 쿠폰 적용 | Order | `POST /api/v1/orders` 변경 | CPN-6, 기존 Order | - | ⬜ todo |

## 순서 산정 근거 (TODO.md 「사이클 순서 산정 근거」 승계)

- **쓰기 → 조회**: 템플릿 등록·수정·삭제(1~3)로 aggregate 골격을 먼저 세우고, 조회 계열(4~5)을 뒤에 둔다.
- **템플릿 → 발급**: 발급(CPN-6)은 템플릿이 있어야 실데이터로 검증 가능. 발급 관련 조회(7~8)는 발급 뒤.
- **ORD-7 마지막**: 발급된 쿠폰이 있어야 사용 처리(USED 전이)·할인 계산·금액 스냅샷·전체 롤백을 검증할 수 있다.

> 본 단계(2)는 **동시성 미고려**. 락·동시성 테스트는 단계 3~4에서 별도로 다룬다.
> ORD-7의 트랜잭션 원자성(롤백)은 동시성과 별개이므로 여기서 일반 테스트로 검증한다.
