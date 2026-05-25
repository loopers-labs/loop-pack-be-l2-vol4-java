# DTO 컨벤션

## 책임
표현 계층의 요청·응답 데이터 구조를 정의한다. 도메인 모델을 표현 계층에 노출하지 않고, application `*Info`를 `Response`로 변환하는 경계 역할을 한다.

## 정식 참조
`apps/commerce-api/src/main/java/com/loopers/interfaces/api/user/UserV1Dto.java`

## 핵심 규칙
- 단일 클래스 `XxxV1Dto` 안에 관련 `Request`/`Response`를 중첩 `record`로 묶는다.
- `Response` record는 `from(XxxInfo)` 정적 팩토리로 application `*Info`를 변환한다. 명명 규약: 매개변수 하나이므로 `from`.
- `Request` record는 기본 Bean Validation으로 null/blank만 1차 방어한다. 도메인 규칙(형식·길이·범위 등) 검증은 VO `from()`이 단일 진실의 원천이며, DTO는 그 규칙을 중복하지 않는다.
  - `String` 필드 → `@NotBlank`.
  - 그 외 타입(`LocalDate`, 수치, enum 등) → `@NotNull`.
  - 컬렉션은 `@NotEmpty`, 양수여야 하는 수치/ID는 `@Positive`, 중첩 객체는 `@Valid`를 추가로 고려한다.
  - 스펙상 nullable한(선택) 필드에는 붙이지 않는다.
  - 모든 어노테이션에 `message`를 명시한다. 형식은 "<필드 의미>는 null이거나 빈 값일 수 없습니다."(`@NotBlank`) / "<필드 의미>는 null일 수 없습니다."(`@NotNull`).
- record 어노테이션 스타일: `어노테이션 1줄` → `필드 1줄` → `개행 1줄` 순으로 필드마다 끊어 쓴다(한 줄에 몰아쓰지 않는다).
- 검증이 동작하려면 컨트롤러 `@RequestBody`에 `@Valid`를 붙이고, `MethodArgumentNotValidException`은 `ApiControllerAdvice`에서 `BAD_REQUEST`(400)로 매핑한다.
- DTO가 도메인 모델(`*Model`)을 직접 참조하지 않는다. `*Info`를 통해 간접 변환한다.

## 핵심 발췌
```java
public class UserV1Dto {

    public record SignUpRequest(
        @NotBlank(message = "로그인 ID는 null이거나 빈 값일 수 없습니다.")
        String loginId,

        @NotBlank(message = "비밀번호는 null이거나 빈 값일 수 없습니다.")
        String password,

        @NotBlank(message = "이름은 null이거나 빈 값일 수 없습니다.")
        String name,

        @NotNull(message = "생년월일은 null일 수 없습니다.")
        LocalDate birthDate,

        @NotBlank(message = "이메일은 null이거나 빈 값일 수 없습니다.")
        String email
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

    public record ChangePasswordRequest(
        @NotBlank(message = "현재 비밀번호는 null이거나 빈 값일 수 없습니다.")
        String currentPassword,

        @NotBlank(message = "새 비밀번호는 null이거나 빈 값일 수 없습니다.")
        String newPassword
    ) {}
}
```

## do / don't
- ✅ `Response.from(XxxInfo)`로 Info → Response 변환을 일원화한다.
- ✅ 관련 Request·Response를 하나의 `XxxV1Dto` 클래스에 중첩 record로 묶는다.
- ✅ `Request`는 `@NotBlank`/`@NotNull`로 null·blank만 1차 방어하고, `message`를 명시한다.
- ✅ record 어노테이션은 `어노테이션 → 필드 → 개행`을 필드마다 반복해 가독성을 유지한다.
- ❌ DTO에 형식·길이·범위 등 도메인 규칙(`@Pattern`, `@Size` 등)을 두지 않는다 — 그 검증은 VO `from()`이 단일 진실의 원천이다.
- ❌ `Response` record에는 검증 어노테이션을 붙이지 않는다.
- ❌ DTO가 `*Model`을 직접 참조하거나 들고 다니지 않는다.
