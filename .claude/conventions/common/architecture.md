# 아키텍처 컨벤션

## 책임
레이어 간 호출 방향·패키지 구조·검증 단일화·인증 메커니즘·엔티티 표현계층 비노출 원칙을 정의한다.

## 정식 참조
`apps/commerce-api/src/main/java/com/loopers/interfaces/api/user/UserV1Controller.java`
`apps/commerce-api/src/main/java/com/loopers/domain/user/UserRepository.java`
`apps/commerce-api/src/main/java/com/loopers/infrastructure/user/UserRepositoryImpl.java`
`apps/commerce-api/src/main/java/com/loopers/interfaces/api/auth/AuthenticatedUser.java`
`apps/commerce-api/src/main/java/com/loopers/interfaces/api/auth/AuthenticatedUserArgumentResolver.java`
`apps/commerce-api/src/main/java/com/loopers/interfaces/api/auth/AdminAuthInterceptor.java`
`apps/commerce-api/src/main/java/com/loopers/interfaces/api/auth/LoginUser.java`
`apps/commerce-api/src/main/java/com/loopers/support/config/WebMvcConfig.java`

## 핵심 규칙

### 호출 방향
```
interfaces → application → domain → infrastructure
```
역방향 의존은 허용하지 않는다. 도메인 `*Repository`는 추상 인터페이스이고 `infrastructure.*RepositoryImpl`이 `*JpaRepository`를 위임 구현한다 — 도메인은 JPA에 직접 의존하지 않는다.

애플리케이션 `Facade`가 도메인 `Repository`와 도메인 서비스를 주입해 유스케이스를 조합하고, 트랜잭션 경계를 가진다. 도메인 서비스는 영속성·트랜잭션이 없는 무상태 객체다.

### 레이어 패키지 맵
```
com.loopers
  interfaces.api.<domain>     Controller, *V1Dto, *V1ApiSpec
  interfaces.api.auth         인증 표현 계층 (어노테이션·ArgumentResolver) — 도메인 무관
  application.<domain>        Facade(@Service·@Transactional 유스케이스 조합), *Info (출력 DTO)
  domain.<domain>             *Model(@Entity), *Repository(인터페이스), 도메인 Service(무상태 @Component)
  infrastructure.<domain>     *RepositoryImpl, *JpaRepository
  support.error               CoreException + ErrorType
  support.config              WebMvcConfig 등 횡단 MVC 설정
```

### 도메인 객체 설계 (모델링)
- **Entity**: 식별자(ID)로 동일성을 판단하고 상태·연속성을 갖는 주체(`*Model`). 상태를 바꾸는 행위를 스스로 책임진다.
- **VO**: "그 값이 무엇이냐"만 중요한 불변 객체. 같은 값이면 같다. 형식·범위 검증을 스스로 보장한다.
- **Domain Service**: 한 객체에 안착하기 애매한, 여러 도메인 객체의 무상태 협력 로직(`domain/service.md`).
- 로직은 우선 그 규칙이 속한 Entity/VO 안에 둔다(rich domain). 빈약한 객체 + 비대한 서비스를 피한다.
- 같은 규칙이 여러 흐름·서비스에 중복되면, 그 규칙이 특정 도메인 객체에 속한다는 신호다 — 객체로 끌어올린다.
- 연산만 하고 고유 상태·도메인 의미가 없는 "doer/Manager" 류를 도메인 개념인 척 만들지 않는다. 필요하면 무상태 도메인 서비스로 명시한다.

### 검증 두 층 — DTO null/blank, VO 형식·길이
검증은 두 층으로 나뉜다. ① 표현 계층 DTO(`Request`)는 `@NotBlank`/`@NotNull`로 null·blank만 1차 방어한다(`@Valid` 필요, `MethodArgumentNotValidException` → `BAD_REQUEST`). ② 도메인 규칙(형식·길이·범위)은 각 VO의 `from()` 정적 팩토리가 단일 진실의 원천으로 수행하고 `CoreException(BAD_REQUEST)`을 던진다(`ApiControllerAdvice`가 400으로 변환). DTO에 `@Pattern`/`@Size` 등 형식·길이 검증을 두지 않는다 — 그건 VO의 책임이다(상세는 `interfaces/dto.md`).

### 엔티티 표현계층 비노출
Controller·Facade는 JPA 엔티티(`*Model`)를 파라미터로 직접 받거나 반환하지 않는다.
- 인증된 사용자는 경량 record `AuthenticatedUser(userId)`로 받고, `loginUser.userId()`만 Facade로 전달한다.
- Facade는 `*Model`을 `*Info`로 변환해 반환하고, Controller는 `*Info`를 `*V1Dto.Response`로 변환한다.

### 인증 메커니즘
사용자 인증과 관리자 인증을 별도 컴포넌트로 분리한다(모두 `interfaces.api.auth` 패키지).

**사용자 인증** — 매 요청 헤더 `X-Loopers-LoginId` / `X-Loopers-LoginPw`를 `ArgumentResolver`가 처리한다.
- `AuthenticatedUserArgumentResolver`가 헤더 추출 → `UserRepository.findActiveByLoginId` → `UserModel.matchesPassword` 순서로 인증하고 `AuthenticatedUser`를 반환한다.
- `support.config.WebMvcConfig`가 이 Resolver를 Spring MVC에 등록한다.
- 인증 실패 사유(헤더 누락 / 헤더 포맷 위반 / 회원 미존재 / 비밀번호 불일치)는 모두 `ErrorType.UNAUTHENTICATED` 단일 응답으로 통합한다 — user enumeration 방지. `errorCode`·`message`·헤더 어디에도 사유 식별 신호를 두지 않는다.
- 컨트롤러 파라미터는 `@LoginUser AuthenticatedUser loginUser` 시그니처만 사용한다.

**관리자 인증** — `/api-admin/**` 경로 진입 전 `AdminAuthInterceptor`(`HandlerInterceptor`)가 선행 검증한다.
- `preHandle`에서 `X-Loopers-Ldap` 헤더가 `loopers.admin`과 일치하는지 확인하고, 아니면 `ErrorType.FORBIDDEN`(403)을 던진다.
- `WebMvcConfig.addInterceptors()`가 `/api-admin/**` 경로 패턴에만 등록한다(`support/config.md`).
- 관리자 컨트롤러는 `@RequestMapping("/api-admin/v1/...")`를 쓰고, 인터셉터가 통과시킨 뒤 별도 인증 파라미터 없이 동작한다.

## 핵심 발췌
```java
// 인증이 필요한 컨트롤러 핸들러 — @LoginUser AuthenticatedUser
@GetMapping("/me")
public ApiResponse<UserV1Dto.MyInfoResponse> readMyInfo(@LoginUser AuthenticatedUser loginUser) {
    UserMyInfo userMyInfo = userFacade.readMyInfo(loginUser.userId());
    return ApiResponse.success(UserV1Dto.MyInfoResponse.from(userMyInfo));
}

// 도메인 Repository — 추상 인터페이스, JPA 의존 없음 (find*/get* 구분은 domain/repository.md)
public interface UserRepository {
    UserModel save(UserModel user);
    UserModel getActiveById(Long id);                       // 없으면 NOT_FOUND
    Optional<UserModel> findActiveByLoginId(String loginId); // 없으면 Optional.empty()
    boolean existsByLoginId(String loginId);
    // ... existsByEmail 등 생략
}

// infrastructure RepositoryImpl — JpaRepository 위임, get*의 NOT_FOUND 변환·soft delete 필터
@Component @RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {
    private final UserJpaRepository userJpaRepository;

    @Override
    public UserModel getActiveById(Long id) {
        return userJpaRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원이 존재하지 않습니다."));
    }

    @Override
    public Optional<UserModel> findActiveByLoginId(String loginId) {
        return userJpaRepository.findByLoginIdValueAndDeletedAtIsNull(loginId);
    }
}

// AuthenticatedUserArgumentResolver — 단일 UNAUTHENTICATED 응답
if (loginId == null || rawPassword == null) {
    throw new CoreException(ErrorType.UNAUTHENTICATED);
}
UserModel user = userRepository.findActiveByLoginId(loginId)
    .orElseThrow(() -> new CoreException(ErrorType.UNAUTHENTICATED));
if (!user.matchesPassword(rawPassword, passwordEncrypter)) {
    throw new CoreException(ErrorType.UNAUTHENTICATED);
}
return new AuthenticatedUser(user.getId());

// AdminAuthInterceptor — /api-admin/** 선행 검증, 실패 시 FORBIDDEN(403)
public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    String ldap = request.getHeader("X-Loopers-Ldap");
    if (!"loopers.admin".equals(ldap)) {
        throw new CoreException(ErrorType.FORBIDDEN);
    }
    return true;
}
```

## do / don't
- ✅ 호출 방향은 항상 `interfaces → application → domain → infrastructure`.
- ✅ 트랜잭션·영속성 접근은 application(Facade)에 둔다. 도메인 서비스는 무상태다.
- ✅ null/blank는 DTO `@NotBlank`/`@NotNull`, 형식·길이·범위는 VO `from()`으로 검증한다.
- ✅ 사용자 인증 실패 사유 전부를 `UNAUTHENTICATED` 하나로 응답한다.
- ✅ 관리자 경로(`/api-admin/**`)는 `AdminAuthInterceptor`가 `X-Loopers-Ldap`를 검증하고 실패 시 `FORBIDDEN`(403).
- ✅ 컨트롤러 파라미터는 `@LoginUser AuthenticatedUser`만 사용한다.
- ❌ 도메인이 JpaRepository에 직접 의존하지 않는다.
- ❌ Controller·Facade가 `*Model`을 파라미터로 받거나 반환하지 않는다.
- ❌ DTO에 형식·길이·범위(`@Pattern`/`@Size`) 검증을 두지 않는다 — 그건 VO `from()`의 책임.
- ❌ 인증 실패 응답에 사유(어떤 헤더가 문제인지, 회원이 없는지 등)를 노출하지 않는다.
