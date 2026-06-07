# Plan: [ID] [제목]

**Spec**: ./spec.md
**작성일**: [DATE]

## 요약

[시나리오 + 기술 접근]

## 기술 컨텍스트

- 고정: Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api
- 시나리오별 추가: [있으면]

## 컨벤션·결정 점검

- [ ] 호출 방향 interfaces → application → domain → infrastructure 준수
- [ ] 검증은 VO `from()`에 단일화 (DTO Bean Validation 미도입)
- [ ] 인증 필요 시 `@LoginUser AuthenticatedUser` 시그니처
- [ ] 해당 결정 반영: [soft/hard delete / 동시성 / 스냅샷 / 멱등 …]

## 레이어별 설계 결정 & 파일 맵

### interfaces
- `interfaces/api/<domain>/<Domain>V1Controller.java` — [엔드포인트: METHOD 경로]
- `<Domain>V1Dto.java` — [요청·응답 record]
- `<Domain>V1ApiSpec.java`

### application
- `application/<domain>/<Domain>Facade.java` — [유스케이스]
- `*Info.java` / `*Command.java`

### domain
- `domain/<domain>/<Domain>Model.java` — [필드·불변식]
- VO: [목록]
- `<Domain>Service.java` — [메서드·ErrorType]
- `<Domain>Repository.java` — [메서드]

### infrastructure
- `infrastructure/<domain>/<Domain>RepositoryImpl.java`
- `<Domain>JpaRepository.java` — [쿼리·페이지·정렬·집계]

## 복잡도 트래킹

| 결정 | 이유 | 기각한 더 단순한 대안 |
|------|------|----------------------|
| [있으면] | | |
