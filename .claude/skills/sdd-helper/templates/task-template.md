# Task: [ID] [제목]

**Plan**: ./plan.md

> 완료한 task는 `- [ ]` → `- [X]`. 구현은 CLAUDE.md·컨벤션 준수.

## Phase F: Foundational (도메인 첫 시나리오만 — 아니면 이 페이즈 삭제)

- [ ] T001 <Domain>Model 작성 `apps/commerce-api/src/main/java/com/loopers/domain/<domain>/<Domain>Model.java`
- [ ] T002 VO 작성 `apps/commerce-api/src/main/java/com/loopers/domain/<domain>/<Vo>.java`
- [ ] T003 <Domain>Repository 인터페이스 `apps/commerce-api/src/main/java/com/loopers/domain/<domain>/<Domain>Repository.java`
- [ ] T004 <Domain>JpaRepository + RepositoryImpl `apps/commerce-api/src/main/java/com/loopers/infrastructure/<domain>/`

## Phase 1: 구현

- [ ] T0xx 도메인: Service 메서드 + 단위 테스트 `.../domain/<domain>/<Domain>Service.java`, `.../<Domain>ServiceTest.java`
- [ ] T0xx 인프라: 조회·영속화 + 통합 테스트 `.../infrastructure/<domain>/`, `.../<Domain>RepositoryIntegrationTest.java`
- [ ] T0xx 애플리케이션: Facade + Info `.../application/<domain>/`
- [ ] T0xx 인터페이스: Controller + Dto + ApiSpec `.../interfaces/api/<domain>/`
- [ ] T0xx E2E 테스트 `.../interfaces/api/<Domain>V1ApiE2ETest.java`

## Phase 2: 마무리

- [ ] T0xx spec 테스트 계획 대비 누락 점검
- [ ] T0xx .http 파일 등 부수 산출물 (필요 시)
