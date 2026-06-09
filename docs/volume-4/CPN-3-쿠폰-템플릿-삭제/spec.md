# Spec: CPN-3 쿠폰 템플릿 삭제

**소스**: `docs/volume-4/01-requirements.md` — CPN-3
**작성일**: 2026-06-10
**상태**: Draft

## 시나리오 요약

관리자가 대상 쿠폰 템플릿을 삭제한다. 삭제는 신규 발급의 중단을 의미하며 soft delete로 처리한다. 관리자 인증을 통과한 요청만 허용하며, 존재하지 않거나 이미 삭제된 템플릿에 삭제 요청이 들어와도 별도 동작 없이 정상 응답으로 마무리한다(멱등 삭제). 이미 발급된 쿠폰(`UserCoupon`)은 발급 시점 스냅샷으로 독립 동작하므로(결정 4) 삭제의 영향을 받지 않으며, 브랜드→상품(BRD-6)과 달리 cascade가 없다.

## 수용 시나리오 (Given/When/Then)

### Main Flow
1. **Given** 관리자 인증 + 대상 템플릿이 활성 상태, **When** 삭제 요청, **Then** 템플릿이 soft delete 처리되고 삭제 완료가 정상 응답된다(200 SUCCESS).
2. **Given** 관리자 인증 + 대상 템플릿이 존재하지 않거나 이미 삭제됨, **When** 삭제 요청, **Then** 별도 동작 없이 정상 응답으로 마무리한다(200 SUCCESS, 멱등).

### Exception Flow
1. **Given** 관리자 인증 실패, **When** 삭제 요청, **Then** 403 FORBIDDEN.

### 비즈니스 규칙
- 삭제는 신규 발급의 중단을 의미한다. 삭제된 템플릿으로는 더 이상 발급할 수 없다(발급 차단은 CPN-6 범위).
- 삭제는 soft delete 방식으로, 행은 보존하되 외부 노출에서는 존재하지 않는 것으로 처리한다(volume-2 결정 7과 일관).
- 존재하지 않거나 이미 삭제된 템플릿에 삭제 요청이 들어와도 별도 처리 없이 정상 응답으로 마무리한다(멱등, volume-2 결정 6과 동일).
- 이미 발급된 쿠폰은 발급 시점 스냅샷으로 독립 동작하므로 삭제의 영향을 받지 않는다(결정 4). cascade 무효화가 없어 발급 쿠폰 일괄 처리도 없다.

## 엣지 케이스
- 대상 템플릿 상태: 활성(삭제) / 미존재(no-op) / 이미 삭제됨(no-op).
- 멱등성: 동일 템플릿에 삭제를 두 번 요청해도 결과가 같고 두 응답 모두 정상.
- 권한 경계: 회원 헤더만 있고 admin 헤더 없으면 403.

## 기능 요구사항
- **FR-001**: 시스템은 관리자 인증 통과 요청만 삭제를 허용한다(실패 403).
- **FR-002**: 시스템은 대상 템플릿이 활성 상태이면 soft delete 처리한다.
- **FR-003**: 시스템은 대상 템플릿이 존재하지 않거나 이미 삭제된 경우 별도 동작 없이 정상 응답(200 SUCCESS)으로 마무리한다(멱등).

## 관련 엔티티
- **CouponModel** (재사용): `BaseEntity.delete()`(멱등 soft delete)를 그대로 사용. 도메인 신규 행위 없음.
- **CouponRepository**: 멱등 처리를 위한 활성 단건 탐색(`findActiveById` — 부재·삭제 시 예외 없이 빈 결과, no-op 분기용) 신규.
- **재사용**: `AdminAuthInterceptor`, `ErrorType`(FORBIDDEN), `ApiResponse.success()`(데이터 없는 200).

## 테스트 계획
| 레벨 | 대상 | 무엇을 단언하는가 |
|------|------|------------------|
| Service/Facade 단위 | 삭제 유스케이스 | 활성 템플릿 → delete 호출 / 미존재·이미 삭제 → no-op(예외 없음) |
| Integration | CouponRepository | 활성 단건 탐색: 활성 present / 삭제·부재 empty |
| E2E | `DELETE /api-admin/v1/coupons/{couponId}` | 200 + SUCCESS & 활성 조회에서 제외 / 동일 요청 반복 200 / 미존재 200 / admin 인증 실패 403 (statusCode + meta.result + errorCode까지, 메시지 문구 미단언) |

## 관련 결정
- **결정 4 (발급 쿠폰 독립)**: 삭제 = 신규 발급 중단만. 발급 쿠폰은 스냅샷으로 독립 — cascade 무효화 없음(BRD-6 상품 cascade와 의도적 차이).
- **멱등 삭제 (volume-2 결정 6)**: 부재·이미 삭제 모두 정상 응답.
- **soft delete (volume-2 결정 7)**: `BaseEntity.deletedAt` 사용.

## 성공 기준 / 범위 밖
- **성공**: 위 모든 수용 시나리오·테스트 green. `DELETE /api-admin/v1/coupons/{couponId}`가 인증·멱등·soft delete 분기를 명세대로 처리.
- **범위 밖**: 삭제 템플릿의 신규 발급 차단 검증(CPN-6), 발급 쿠폰 회수(무효화), hard delete, 복원 API.
