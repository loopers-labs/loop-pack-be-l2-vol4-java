# ApiSpec 컨벤션

## 책임
SpringDoc OpenAPI 문서화 전용 인터페이스. Controller가 `implements`해 핸들러 시그니처를 공유하되, Swagger 어노테이션은 이 인터페이스에 집중시켜 Controller 본문을 깔끔하게 유지한다.

## 정식 참조
`apps/commerce-api/src/main/java/com/loopers/interfaces/api/user/UserV1ApiSpec.java`

## 핵심 규칙
- 인터페이스명은 `*V1ApiSpec`. Controller가 `implements`한다.
- 인터페이스 레벨에 `@Tag(name = "...", description = "...")`를 붙여 API 그룹을 정의한다.
- 각 핸들러 메서드에 `@Operation(summary = "...", description = "...")`을 붙인다. `summary`는 짧은 행위 제목, `description`은 상세 동작 설명.
- `@LoginUser AuthenticatedUser` 파라미터는 문서에 노출되면 안 되므로 `@Parameter(hidden = true)`를 붙인다.
- Controller 본문에는 문서 어노테이션을 두지 않는다. 시그니처 일치는 `@Override`로 보장한다.
- 사용 어노테이션은 `@Tag`, `@Operation`, `@Parameter` 셋으로 충분하다. 실제 `UserV1ApiSpec.java`도 이 셋만 쓴다. `@ApiResponse`·`@Schema`는 정당한 문서화 필요가 있을 때만 예외적으로 추가한다(기본은 미사용).

## 핵심 발췌
```java
@Tag(name = "User V1 API", description = "Loopers 회원 도메인 API 입니다.")
public interface UserV1ApiSpec {

    @Operation(
        summary = "회원 가입",
        description = "신규 회원을 등록한다."
    )
    ApiResponse<UserV1Dto.SignUpResponse> signUp(UserV1Dto.SignUpRequest request);

    @Operation(
        summary = "내 정보 조회",
        description = "본인 인증된 회원의 정보를 반환한다. 이름은 마지막 1글자가 *로 마스킹되어 반환된다."
    )
    ApiResponse<UserV1Dto.MyInfoResponse> readMyInfo(
        @Parameter(hidden = true) @LoginUser AuthenticatedUser loginUser
    );

    @Operation(
        summary = "비밀번호 수정",
        description = "본인 인증된 회원의 비밀번호를 새 값으로 교체한다. 본문 currentPassword로 변경 의도를 재확인하고, newPassword가 RULE(8~16자, 영문/숫자/특수문자, 생년월일 포함 불가)을 만족하면 BCrypt 해시로 갱신한다."
    )
    ApiResponse<Void> changePassword(
        @Parameter(hidden = true) @LoginUser AuthenticatedUser loginUser,
        UserV1Dto.ChangePasswordRequest request
    );
}
```

## do / don't
- ✅ 문서 어노테이션(`@Tag`, `@Operation`, `@Parameter`)은 ApiSpec 인터페이스에 모은다.
- ✅ `@LoginUser AuthenticatedUser` 파라미터에는 `@Parameter(hidden = true)`를 붙인다.
- ❌ Controller 본문에 `@Operation`·`@Tag` 등 Swagger 어노테이션을 두지 않는다.
- ❌ `@Schema`·`@ApiResponse`를 실제 필요 없이 과잉 추가하지 않는다 (실제 참조 파일에는 없음).
