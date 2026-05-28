# Spec: LIK-1 좋아요 등록

**소스**: `docs/volume-2/01-requirements.md` — LIK-1
**작성일**: 2026-05-27
**상태**: Draft

## 시나리오 요약

로그인한 회원이 특정 상품에 좋아요를 누른다. 회원 인증을 통과한 요청만 허용하며, 대상 상품이 존재하고 삭제되지 않았는지 확인한 뒤, 해당 회원·상품 조합의 좋아요가 없으면 새로 저장하고 이미 있으면 별도 동작 없이 정상 응답으로 마무리한다(멱등 등록). 한 회원의 한 상품 좋아요는 한 건만 유지된다(`(user_id, product_id)` UNIQUE 자연키 + 존재 검사). Like 도메인의 첫 시나리오라 Like aggregate 골격(`Like` 모델 + `LikeRepository`)을 새로 세운다. 회원 인증 토대(`@LoginUser AuthenticatedUser`)와 상품 활성 검증(`ProductRepository`)은 기존 것을 재사용한다. (동시 등록 요청에 대한 명시적 동시성 처리·테스트는 본 cycle 범위 밖 — 추후 동시성 과제.)

## 수용 시나리오 (Given/When/Then)

### Main Flow
1. **Given** 로그인한 회원과 활성 상품이 있고 아직 좋아요하지 않았을 때, **When** 좋아요 등록을 요청하면, **Then** 좋아요가 저장되고 등록 완료가 정상 응답된다(200 SUCCESS).
2. **Given** 로그인한 회원이 이미 좋아요한 상품일 때, **When** 다시 좋아요 등록을 요청하면, **Then** 별도 동작 없이 정상 응답으로 마무리하고 좋아요 행은 여전히 한 건이다(멱등 등록).

### Alternate Flow
1. **Given** 동일 회원이 동일 상품에 짧은 시간 안에 좋아요 등록을 여러 번(순차) 요청할 때, **When** 처리되면, **Then** 존재 검사로 두 번째부터는 저장 없이 정상 응답하며 좋아요 행은 한 건만 유지된다(멱등). (동시 요청 동시성은 범위 밖.)

### Exception Flow
1. **Given** 회원 인증 정보가 없거나 `X-Loopers-LoginId/LoginPw`가 올바르지 않을 때, **When** 등록을 요청하면, **Then** 인증 실패로 응답한다(401 UNAUTHENTICATED).
2. **Given** 대상 상품이 존재하지 않거나 이미 삭제(상품 삭제·브랜드 cascade 삭제 포함)된 상태일 때, **When** 등록을 요청하면, **Then** 자원을 찾을 수 없다고 응답한다(404 NOT_FOUND).

### 비즈니스 규칙
- 한 회원의 한 상품에 대한 좋아요는 한 건만 유지된다(`(user_id, product_id)` UNIQUE).
- 좋아요 등록 동작은 진입 경로(목록/상세)와 무관하게 같다.
- 이미 좋아요한 상품에 다시 등록 요청이 들어와도 별도 처리 없이 정상 응답으로 마무리한다(멱등 등록).
- 삭제된 상품(브랜드 cascade 삭제 포함, BRD-6)에는 좋아요를 새로 등록할 수 없다.

## 엣지 케이스

- 대상 상품 상태: 활성(등록) / 미존재(404) / 삭제됨(404, 브랜드 cascade 삭제 포함).
- 멱등성: 동일 회원·상품에 (순차) 두 번 등록해도 행은 한 건, 두 응답 모두 정상.
- 권한 경계: 회원 인증 헤더 누락·불일치 → 401.
- 다른 회원이 같은 상품을 좋아요하는 것은 별개 행(서로 영향 없음).

## 기능 요구사항

- **FR-001**: 시스템은 회원 인증(`X-Loopers-LoginId/LoginPw`)을 통과한 요청만 좋아요 등록을 허용해야 한다. 실패 시 401 UNAUTHENTICATED로 응답한다.
- **FR-002**: 시스템은 대상 상품이 존재하고 삭제되지 않았는지 검증해야 한다. 아니면 404 NOT_FOUND로 응답한다.
- **FR-003**: 시스템은 해당 회원·상품 조합의 좋아요가 없으면 저장하고, 이미 있으면 저장하지 않아야 한다(멱등 등록).
- **FR-004**: 시스템은 한 회원·한 상품에 대해 좋아요 행을 한 건만 유지해야 한다(존재 검사 + `(user_id, product_id)` UNIQUE 자연키). 동시 요청에 대한 명시적 동시성 처리는 범위 밖.

## 관련 엔티티

- **Like** (신규 aggregate): 회원 식별자(userId)·상품 식별자(productId)를 보유. `@Builder`로 생성. 회원·상품 존재 검증은 응용 계층 책임. `userId`·`productId`는 다른 aggregate로의 식별자 참조라 VO 없이 `Long`으로 직접 보유(`ProductModel.brandId` 선례). 취소(hard delete)는 LIK-2 범위.
- **LikeRepository** (신규): 저장, 회원·상품 조합 존재 여부 조회. (취소용 삭제는 LIK-2.)
- **재사용**: `UserRepository`(회원 존재 검증 — 없으면 응용 계층이 NOT_FOUND), `ProductRepository`(상품 활성 존재 검증 — 없으면 응용 계층이 NOT_FOUND), `@LoginUser AuthenticatedUser`(회원 인증), `ErrorType`(NOT_FOUND·UNAUTHENTICATED).

## 테스트 계획

| 레벨 | 대상 | 무엇을 단언하는가 |
|------|------|------------------|
| Model 단위 | Like | 생성 시 userId·productId 보유 |
| Service/Facade 단위 | 좋아요 등록 유스케이스 | 상품 미존재/삭제 시 NOT_FOUND / 미등록 시 저장 / 이미 등록 시 저장 안 함(멱등) |
| Integration | LikeRepository | 저장·조회 / 동일 `(user_id, product_id)` 직접 중복 저장 시 UNIQUE 제약 위반(단일 스레드 — 제약 존재 확인. UserRepository login_id UNIQUE 테스트 선례) |
| E2E | `POST /api/v1/products/{productId}/likes` | 200 + meta.result SUCCESS & 좋아요 저장 / 반복 등록 200(행 1건) / 회원 인증 실패 401 / 상품 미존재 404 (statusCode + meta.result + errorCode까지, 메시지 문구는 단언 안 함) |

## 관련 결정

- **결정 7 (소프트 삭제 C — 좋아요 hard delete)**: 좋아요는 토글이 빈번하고 이력 가치가 낮아 hard delete. 활성 행 UNIQUE 보장이 단순해진다. → 등록은 일반 insert, 취소(LIK-2)는 행 제거. `(user_id, product_id)` UNIQUE가 멱등·중복을 보장.
- **결정 1 (좋아요 수 집계 A)**: 좋아요 수는 조회 시 매번 집계(별도 카운터 컬럼 없음). 등록 시 카운터 증가 같은 부수 동작 없음 — LIK-1은 행 한 건 보장만. (집계 조회는 PRD-1·PRD-2 범위.)
- **본 cycle 신규 결정 (동시성 처리 보류)**: 멱등 등록은 존재 검사(있으면 저장 안 함)로 처리한다. `(user_id, product_id)` UNIQUE는 ERD의 자연키로 스키마에 유지하되, 동시 등록 경합에 대한 명시적 처리(제약 위반 graceful 핸들링)와 동시성 테스트는 본 cycle에서 다루지 않는다 — 추후 동시성 전용 과제에서 도입. (사용자 결정)

## 성공 기준 / 범위 밖

- **성공**: 위 모든 수용 시나리오·테스트 계획이 green. `POST /api/v1/products/{productId}/likes`가 인증·상품 검증·멱등 등록 분기를 명세대로 처리.
- **범위 밖**: 동시 등록 동시성 처리·테스트(추후 동시성 과제), 좋아요 취소(LIK-2), 좋아요한 상품 목록(LIK-3), 좋아요 수 집계 노출(PRD-1·PRD-2), 좋아요 이력/이벤트 로그.
