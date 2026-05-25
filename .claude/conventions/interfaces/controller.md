# Controller 컨벤션

## 책임
HTTP 요청을 수신해 Facade에 위임하고, 결과를 `ApiResponse`로 래핑해 반환하는 표현 계층의 진입점. 비즈니스 로직·검증은 포함하지 않는다.

## 정식 참조
`apps/commerce-api/src/main/java/com/loopers/interfaces/api/user/UserV1Controller.java`

## 핵심 규칙
- `@RestController` + `@RequestMapping("/api/v1/<복수도메인>")` + `@RequiredArgsConstructor`로 선언한다.
- `*V1ApiSpec` 인터페이스를 `implements`한다. 문서 어노테이션은 ApiSpec에 있으므로 Controller 본문은 깔끔하게 유지된다.
- 주입 대상은 Facade 하나뿐이다. 다른 Service·Repository를 직접 주입하지 않는다.
- 모든 응답은 `ApiResponse`로 래핑한다. 데이터가 있으면 `ApiResponse.success(data)`, 데이터가 없으면 `ApiResponse<Void>` 반환 타입 + 무인자 `ApiResponse.success()`.
- 생성 엔드포인트에는 `@ResponseStatus(HttpStatus.CREATED)`를 붙인다.
- 인증이 필요한 핸들러는 `@LoginUser AuthenticatedUser loginUser` 파라미터를 받는다. Facade에는 `loginUser.userId()`만 전달한다 — 엔티티(`*Model`)를 파라미터로 넘기지 않는다.
- 요청 본문은 `@RequestBody XxxV1Dto.XxxRequest`로 받고, DTO 필드를 Facade의 raw 파라미터로 풀어서 전달한다.

## 핵심 발췌
```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserV1Controller implements UserV1ApiSpec {

    private final UserFacade userFacade;

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserV1Dto.SignUpResponse> signUp(@RequestBody UserV1Dto.SignUpRequest request) {
        UserSignUpInfo newUserSignUpInfo = userFacade.signUp(
            request.loginId(),
            request.password(),
            request.name(),
            request.birthDate(),
            request.email()
        );
        return ApiResponse.success(UserV1Dto.SignUpResponse.from(newUserSignUpInfo));
    }

    @Override
    @GetMapping("/me")
    public ApiResponse<UserV1Dto.MyInfoResponse> readMyInfo(@LoginUser AuthenticatedUser loginUser) {
        UserMyInfo userMyInfo = userFacade.readMyInfo(loginUser.userId());
        return ApiResponse.success(UserV1Dto.MyInfoResponse.from(userMyInfo));
    }

    @Override
    @PatchMapping("/me/password")
    public ApiResponse<Void> changePassword(@LoginUser AuthenticatedUser loginUser,
                                            @RequestBody UserV1Dto.ChangePasswordRequest request) {
        userFacade.changePassword(loginUser.userId(), request.currentPassword(), request.newPassword());
        return ApiResponse.success();
    }
}
```

## do / don't
- ✅ 응답은 반드시 `ApiResponse`로 래핑한다.
- ✅ 데이터 없는 응답은 `ApiResponse<Void>` + `ApiResponse.success()`.
- ✅ 인증 파라미터는 `@LoginUser AuthenticatedUser`만, Facade에는 `userId()`만 넘긴다.
- ✅ 생성 요청에는 `@ResponseStatus(HttpStatus.CREATED)`를 붙인다.
- ❌ 컨트롤러에 비즈니스 로직·검증을 두지 않는다.
- ❌ `*Model`(엔티티)을 직접 반환하거나 Facade 파라미터로 전달하지 않는다.
- ❌ Facade 외 다른 빈을 직접 주입하지 않는다.
