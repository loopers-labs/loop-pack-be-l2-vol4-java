# Spec: BRD-1 브랜드 조회 (public 단건)

**소스**: `docs/volume-2/01-requirements.md` — BRD-1
**작성일**: 2026-05-25
**상태**: Draft

## 시나리오 요약

회원 또는 비회원이 별도 로그인 없이 특정 브랜드의 정보를 확인한다. 대상 브랜드가 삭제되지 않고 존재하면 식별자·이름·설명을 반환하고, 관리자 전용 정보(등록·갱신 시각, 운영 메모 등)는 응답에서 제외한다. Brand 도메인 첫 대고객(public) 엔드포인트이므로 public 표현 계층을 신설한다.

## 수용 시나리오 (Given/When/Then)

### Main Flow
1. **Given** 활성 브랜드가 존재, **When** (무인증) 식별자로 조회 요청, **Then** 식별자·이름·설명이 반환된다(200).

### Exception Flow
1. **Given** 대상 브랜드가 부재거나 이미 삭제됨, **When** 조회 요청, **Then** 404 NOT_FOUND.

### 비즈니스 규칙
- 별도 로그인 없이 이용 가능.
- 관리자 전용 정보(등록·갱신 시각, 운영 메모)는 응답에서 제외.

## 엣지 케이스
- 삭제된 브랜드 조회(404) / 존재하지 않는 id(404).
- 설명이 null인 브랜드(설명 null로 반환).

## 기능 요구사항
- **FR-001**: 시스템은 인증 없이 조회를 허용한다.
- **FR-002**: 시스템은 활성 브랜드만 노출한다(부재/삭제 404).
- **FR-003**: 시스템은 식별자·이름·설명만 반환하고 관리자 전용 정보는 제외한다.

## 관련 엔티티
- **BrandModel**: 읽기.
- **BrandRepository**: 활성 브랜드 단건 조회(존재 보장) — BRD-3·BRD-5와 공유.
- **신규 public 표현 계층**: `BrandV1Controller`(`/api/v1/brands`) / `BrandV1Dto` / `BrandV1ApiSpec`. admin 응답과 달리 시각·운영 정보 미포함.

## 테스트 계획
| 레벨 | 대상 | 무엇을 단언하는가 |
|------|------|------------------|
| Service/Facade 단위 | 조회 유스케이스 | 활성 존재 시 정보 반환, 부재/삭제 시 NOT_FOUND |
| Integration | BrandRepository | 활성 단건 조회가 삭제 행을 제외 |
| E2E | `GET /api/v1/brands/{brandId}` | 200 + 응답 키(id·name·description, 시각 미포함) / 404 |

## 관련 결정
- 결정 7(soft delete): 삭제 브랜드는 조회에서 제외.
- public/admin 응답 shape 분리(BRD-4 plan에서 합의한 컨트롤러 분리 선례 — public 컨트롤러 신설).

## 성공 기준 / 범위 밖
- 성공: 위 수용 시나리오·테스트 green. `GET /api/v1/brands/{brandId}`가 무인증 조회·활성 필터·필드 제한을 명세대로 처리.
- 범위 밖: 인증, 관리자 전용 필드 노출, 목록 조회(BRD-2).
