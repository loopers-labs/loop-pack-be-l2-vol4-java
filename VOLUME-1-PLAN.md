# VOLUME-1 DEVELOPMENT PLAN (Outside-In)

이 문서는 Outside-In 방식의 TDD를 따르는 회원(Member) 기능 구현 계획입니다.
API 컨트롤러부터 시작하여 페사드, 서비스, 도메인 엔티티 순으로 외부에서 내부로 구현을 진행합니다.

## Phase 1: 회원가입 (Sign Up)
- [ ] 1.1 API 컨트롤러 구현 (`POST /v1/members/signup`)
    - [ ] DTO 정의 및 엔드포인트 테스트 (Mock Facade 사용)
- [ ] 1.2 `MemberFacade` 구현
    - [ ] 오케스트레이션 로직 테스트 (Mock Service 사용)
- [ ] 1.3 `MemberService` 및 도메인 로직 구현
    - [ ] 중복 ID 체크, 비밀번호 유효성 로직 (Mock Repository 사용)
- [ ] 1.4 `Member` 엔티티 및 Repository 구현 (Infrastructure)

## Phase 2: 내 정보 조회 (Get My Info)
- [ ] 2.1 API 컨트롤러 구현 (`GET /v1/members/me`)
    - [ ] 인증 헤더 처리 및 응답 포맷 테스트 (Mock Facade 사용)
- [ ] 2.2 `MemberFacade` 구현
    - [ ] 조회 결과 가공 및 마스킹 로직 테스트
- [ ] 2.3 `MemberService` 조회 기능 구현

## Phase 3: 비밀번호 수정 (Update Password)
- [ ] 3.1 API 컨트롤러 구현 (`PATCH /v1/members/me/password`)
    - [ ] 요청 데이터 검증 테스트
- [ ] 3.2 `MemberFacade` 구현
- [ ] 3.3 `MemberService` 비밀번호 변경 로직 구현
    - [ ] 현재 비밀번호 일치 확인, 신규 규칙 검증 로직

## Phase 4: 인프라 및 최종 통합
- [ ] 4.1 JPA 엔티티 상세 설정 (BaseEntity, Soft Delete 등)
- [ ] 4.2 데이터베이스 연동 통합 테스트
- [ ] 4.3 전체 시나리오 E2E 테스트
