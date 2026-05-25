# 코드 컨벤션 (인덱스)

모든 코드 작업은 이 폴더의 컨벤션을 준수한다. 각 파일은 책임·정식 참조(코드 원천)·핵심 규칙·발췌·do/don't로 구성된다. **규칙이 코드와 어긋나면 정식 참조 코드가 원천**이며, User 도메인이 정식 참조 구현이므로 새 도메인은 그 레이어·네이밍을 그대로 본뜬다.

## 길잡이
- `common/*`는 모든 코드·테스트에 항상 적용된다.
- 새 도메인은 `domain → infrastructure → application → interfaces` 순으로 각 타입 파일을 참조해 작성한다.
- 테스트는 해당 레이어 `test.md` + `common/testing.md`를 함께 본다.

## 레이어/타입별 인덱스

| 레이어 | 파일 | 다루는 컨벤션 | 이런 코드 작성 시 참조 |
|---|---|---|---|
| common | [common/naming.md](common/naming.md) | 변수·클래스·메서드·정적 팩토리 네이밍 | 모든 코드 |
| common | [common/architecture.md](common/architecture.md) | 호출 방향·레이어 경계·도메인 모델링·검증 단일화·인증·엔티티 비노출 | 모든 코드(설계 결정) |
| common | [common/code-style.md](common/code-style.md) | 주석·`String.format`·매직넘버·에러 메시지 분리 | 모든 코드 |
| common | [common/testing.md](common/testing.md) | AssertJ 단언·3A 주석·BDDMockito·`@DisplayName`·에러 단언 범위·픽스처·`@Nested` | 모든 테스트 |
| domain | [domain/model.md](domain/model.md) | `@Entity` 애그리거트 루트(불변식·VO 합성·빌더) | `*Model` |
| domain | [domain/vo.md](domain/vo.md) | `@Embeddable` record VO(`from()` 검증·상수) | VO |
| domain | [domain/service.md](domain/service.md) | 도메인 서비스(무상태 `@Component` POJO·객체 협력) | `*Service` |
| domain | [domain/repository.md](domain/repository.md) | 도메인 추상 `Repository` 인터페이스 | 도메인 `*Repository` |
| domain | [domain/test.md](domain/test.md) | 도메인 단위 테스트(Model/VO/Service) | `*ModelTest`·`*ServiceTest`·VO 테스트 |
| application | [application/facade.md](application/facade.md) | `@Service`+`@Transactional` 유스케이스 조합(Repository+도메인 서비스 주입, Model→Info) | `*Facade` |
| application | [application/info.md](application/info.md) | 출력 DTO record(`from(Model)`) | `*Info` |
| application | [application/test.md](application/test.md) | Facade 단위 테스트 관점 | `*FacadeTest` |
| infrastructure | [infrastructure/repositoryImpl.md](infrastructure/repositoryImpl.md) | `RepositoryImpl`+`JpaRepository`(위임·VO 파생쿼리)·port 어댑터 | `*RepositoryImpl`·`*JpaRepository` |
| infrastructure | [infrastructure/test.md](infrastructure/test.md) | 통합 테스트(Testcontainers·`@Transactional` 미사용) | `*RepositoryIntegrationTest` |
| interfaces | [interfaces/controller.md](interfaces/controller.md) | `@RestController`(ApiResponse 래핑·`@LoginUser`) | `*V1Controller` |
| interfaces | [interfaces/dto.md](interfaces/dto.md) | `V1Dto` 중첩 record(Request/Response, Bean Validation 미도입) | `*V1Dto` |
| interfaces | [interfaces/apiSpec.md](interfaces/apiSpec.md) | SpringDoc 문서화 인터페이스 | `*V1ApiSpec` |
| interfaces | [interfaces/test.md](interfaces/test.md) | E2E 테스트(TestRestTemplate·컨트랙트 단언) | `*V1ApiE2ETest` |
| support | [support/exception.md](support/exception.md) | `CoreException`+`ErrorType`·`ApiControllerAdvice` 매핑 | 예외·에러 응답 |
| support | [support/config.md](support/config.md) | 횡단 MVC 설정(`WebMvcConfig`·ArgumentResolver 등록) | 횡단 설정 |
