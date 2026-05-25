# DTO 컨벤션

## 책임
표현 계층의 요청·응답 데이터 구조를 정의한다. 도메인 모델을 표현 계층에 노출하지 않고, application `*Info`를 `Response`로 변환하는 경계 역할을 한다.

## 정식 참조
`apps/commerce-api/src/main/java/com/loopers/interfaces/api/user/UserV1Dto.java`

## 핵심 규칙
- 단일 클래스 `XxxV1Dto` 안에 관련 `Request`/`Response`를 중첩 `record`로 묶는다.
- `Response` record는 `from(XxxInfo)` 정적 팩토리로 application `*Info`를 변환한다. 명명 규약: 매개변수 하나이므로 `from`.
- `Request` record는 plain record로 선언한다. Bean Validation 어노테이션(`@NotBlank`, `@Pattern`, `@Size` 등)을 붙이지 않는다 — 검증은 VO `from()`에 단일화돼 있다(DRY).
- VO 없는 도메인의 경우에만 DTO Bean Validation 단독 허용.
- DTO가 도메인 모델(`*Model`)을 직접 참조하지 않는다. `*Info`를 통해 간접 변환한다.

## 핵심 발췌
```java
public class UserV1Dto {

    public record SignUpRequest(
        String loginId, String password, String name,
        LocalDate birthDate, String email
    ) {}

    public record SignUpResponse(Long userId, String loginId) {

        public static SignUpResponse from(UserSignUpInfo userSignUpInfo) {
            return new SignUpResponse(userSignUpInfo.userId(), userSignUpInfo.loginId());
        }
    }

    public record MyInfoResponse(String loginId, String name, LocalDate birthDate, String email) {

        public static MyInfoResponse from(UserMyInfo userMyInfo) {
            return new MyInfoResponse(
                userMyInfo.loginId(),
                userMyInfo.name(),
                userMyInfo.birthDate(),
                userMyInfo.email()
            );
        }
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword) {}
}
```

## do / don't
- ✅ `Response.from(XxxInfo)`로 Info → Response 변환을 일원화한다.
- ✅ 관련 Request·Response를 하나의 `XxxV1Dto` 클래스에 중첩 record로 묶는다.
- ❌ `Request`에 `@NotBlank` 등 Bean Validation을 두지 않는다 (VO 검증 단일화, DRY 위반).
- ❌ DTO가 `*Model`을 직접 참조하거나 들고 다니지 않는다.
