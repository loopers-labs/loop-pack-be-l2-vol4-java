# Spec: PRD-7 상품 삭제

**소스**: `docs/volume-2/01-requirements.md` — PRD-7
**작성일**: 2026-05-27
**상태**: Draft

## 시나리오 요약

관리자가 대상 상품을 삭제한다. 관리자 인증을 통과한 요청만 허용하며, 대상 상품이 존재하고 삭제되지 않은 경우 soft delete(행 보존, 외부 노출에서 제외) 처리한다. 존재하지 않거나 이미 삭제된 상품에 삭제 요청이 들어와도 별도 동작 없이 정상 응답으로 마무리한다(멱등 삭제, 결정 6). 삭제는 `BaseEntity`의 soft delete를 사용한다(결정 7). PRD-5에서 세운 Product aggregate에 활성 단건 조회(멱등 처리를 위해 부재 시 예외 없이 빈 결과)를 추가한다. 관리자 인증 토대는 BRD-4에서 만든 것을 재사용한다.

## 수용 시나리오 (Given/When/Then)

### Main Flow
1. **Given** 관리자 인증 정보를 보유하고 대상 상품이 활성 상태일 때, **When** 삭제를 요청하면, **Then** 상품이 soft delete 처리되고(`deletedAt` 기록) 삭제 완료가 정상 응답된다(200 SUCCESS).
2. **Given** 관리자 인증 정보를 보유하고 대상 상품이 존재하지 않을 때, **When** 삭제를 요청하면, **Then** 별도 동작 없이 정상 응답으로 마무리한다(200 SUCCESS, 멱등).
3. **Given** 관리자 인증 정보를 보유하고 대상 상품이 이미 삭제된 상태일 때, **When** 삭제를 요청하면, **Then** 별도 동작 없이 정상 응답으로 마무리한다(200 SUCCESS, 멱등).

### Exception Flow
1. **Given** 관리자 인증 정보가 없거나 `X-Loopers-Ldap` 값이 올바르지 않을 때, **When** 삭제를 요청하면, **Then** 인증 실패로 응답한다(403 FORBIDDEN).

### 비즈니스 규칙
- 삭제는 기술적으로 soft delete 방식으로, 행은 보존하되 외부 노출에서는 존재하지 않는 것으로 처리한다. (결정 7 참조)
- 존재하지 않거나 이미 삭제된 상품에 삭제 요청이 들어와도 별도 처리 없이 정상 응답으로 마무리한다(멱등 삭제). (결정 6 참조)
- 이미 주문된 상품 항목은 주문 시점 스냅샷(결정 5)이 보존되어 있어, 상품이 삭제되어도 과거 주문 표시는 유지된다. (Order 도메인 미구현 — 본 cycle 검증 범위 밖)

## 엣지 케이스

- 대상 상태: 활성(삭제 처리) / 미존재(no-op) / 이미 삭제됨(no-op).
- 멱등성: 동일 상품에 삭제를 두 번 요청해도 결과가 같고(한 번만 `deletedAt` 기록) 두 응답 모두 정상.
- 권한 경계: 회원 인증 헤더(`X-Loopers-LoginId/Pw`)만 있고 admin 헤더가 없으면 인증 실패(403).
- 삭제 후 외부 노출: 삭제된 상품은 활성 조회에서 제외된다(행은 보존).

## 기능 요구사항

- **FR-001**: 시스템은 관리자 인증(`X-Loopers-Ldap: loopers.admin`)을 통과한 요청만 상품 삭제를 허용해야 한다. 실패 시 403 FORBIDDEN으로 응답한다.
- **FR-002**: 시스템은 대상 상품이 활성 상태이면 soft delete(`deletedAt` 기록) 처리해야 한다.
- **FR-003**: 시스템은 대상 상품이 존재하지 않거나 이미 삭제된 경우 별도 동작 없이 정상 응답(200 SUCCESS)으로 마무리해야 한다(멱등 삭제).
- **FR-004**: 삭제는 soft delete로 행을 보존하되, 삭제된 상품은 활성 조회에서 제외되어야 한다.

## 관련 엔티티

- **ProductModel** (재사용): `BaseEntity.delete()`(멱등 soft delete)를 그대로 사용. 도메인 신규 행위 없음.
- **ProductRepository** (편집): 멱등 처리를 위한 활성 단건 조회를 추가. 부재·삭제 시 예외 없이 빈 결과(no-op 분기용). PRD-6의 `getActiveById`(NOT_FOUND throw)와 의미가 다른 별도 조회.
- **재사용**: `AdminAuthInterceptor`(admin 인증), `ErrorType`(FORBIDDEN), `ApiResponse.success()`(데이터 없는 200).

## 테스트 계획

| 레벨 | 대상 | 무엇을 단언하는가 |
|------|------|------------------|
| Service/Facade 단위 | 상품 삭제 유스케이스 | 활성 상품 → `deletedAt` 기록 / 미존재 → no-op(예외 없음) / 이미 삭제 → no-op(멱등, 추가 변화 없음) |
| Integration | ProductRepository | 활성 단건 조회: 활성 present / 삭제·부재 empty |
| E2E | `DELETE /api-admin/v1/products/{productId}` | 200 + meta.result SUCCESS & 활성 조회에서 제외 / 동일 요청 반복 200 / 미존재 200 / admin 인증 실패 403 (statusCode + meta.result + errorCode까지, 메시지 문구는 단언 안 함) |

## 관련 결정

- **결정 6 (DELETE 멱등 처리 B)**: 부재·이미 삭제 모두 정상 응답으로 통일(완전 멱등). RFC 7231 의미와 일관.
- **결정 7 (소프트 삭제 C — 상품)**: 상품은 감사 추적·과거 주문 표시 가치가 있어 soft delete. `BaseEntity.deletedAt` 사용.
- **결정 5 (주문 항목 스냅샷 B)**: 과거 주문 표시는 스냅샷으로 보존 → 상품 삭제와 무관. Order 미구현이라 본 cycle 검증 범위 밖.

## 성공 기준 / 범위 밖

- **성공**: 위 모든 수용 시나리오·테스트 계획이 green. `DELETE /api-admin/v1/products/{productId}`가 인증·멱등 삭제 분기를 명세대로 처리.
- **범위 밖**: 브랜드 삭제 시 상품 cascade(BRD-6), 주문 스냅샷 검증(Order 미구현), 좋아요 cascade(Like 미구현), hard delete, 삭제된 상품 복원 API.
