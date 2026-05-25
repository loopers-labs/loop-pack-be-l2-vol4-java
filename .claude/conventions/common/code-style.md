# 코드 스타일 컨벤션

## 책임
주석·문자열 포매팅·매직넘버·에러 메시지 작성 방식을 통일해 코드 잡음을 줄인다.

## 정식 참조
`apps/commerce-api/src/main/java/com/loopers/domain/user/LoginId.java`

## 핵심 규칙

### 주석
- 기본적으로 주석을 쓰지 않는다.
- WHY가 비자명한 경우에만 한 줄 주석을 허용한다.
- 금지 형태:
  - 데이터 옆 인라인 주석: `"abcd", // 4자 최소`
  - 표시용 어노테이션 파라미터: `@ParameterizedTest(name = "...")`
  - 가독성을 위한 잉여 빈 줄
- 의도는 `@DisplayName` · plan.md · 채팅에 담는다.

### 문자열 포매팅
- 변수가 들어가는 문자열은 `+` 결합 대신 `String.format("...%d~%d...", a, b)`로 작성한다.
- 대상: 예외 메시지, 사용자 노출 텍스트 전반.
- 예외: 로그 메시지의 SLF4J `{}` 플레이스홀더는 별개 규칙을 따른다.

### 매직넘버 상수화
- 도메인 규칙 임계값(길이 상·하한, 패턴 등)은 의미 있는 이름의 `private static final` 상수로 추출한다.
- 사용자 노출 메시지 안의 숫자도 같은 상수를 결합해 표현한다.
- 제외: `0`, `1` 같은 트리비얼 케이스와 테스트 데이터.

### 에러 메시지 — 사유별 분리
- 한 검증에 사유가 둘 이상이면 통합 메시지 대신 사유별로 별도 메시지를 작성한다.
- 예: "로그인 ID는 4~20자만 허용됩니다." 와 "로그인 ID는 영문 및 숫자만 허용됩니다."를 분리.

## 핵심 발췌
```java
// 매직넘버 상수화 + String.format + 사유별 메시지 분리
private static final int MIN_LENGTH = 4;
private static final int MAX_LENGTH = 20;
private static final Pattern ALLOWED_PATTERN = Pattern.compile("[A-Za-z0-9]+");

public static LoginId from(String value) {
    if (value == null || value.isBlank()) {
        throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 필수입니다.");
    }
    if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
        throw new CoreException(ErrorType.BAD_REQUEST,
            String.format("로그인 ID는 %d~%d자만 허용됩니다.", MIN_LENGTH, MAX_LENGTH));
    }
    if (!ALLOWED_PATTERN.matcher(value).matches()) {
        throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문 및 숫자만 허용됩니다.");
    }
    return new LoginId(value);
}
```

## do / don't
- ✅ 임계값은 `private static final` 상수로 추출한다.
- ✅ 변수가 들어가는 메시지는 `String.format`으로 작성한다.
- ✅ 검증 사유별로 에러 메시지를 분리한다.
- ❌ 코드 동작을 반복 설명하는 인라인 주석을 달지 않는다.
- ❌ 가독성을 위한 잉여 빈 줄이나 표시용 어노테이션 옵션을 쓰지 않는다.
- ❌ 여러 검증 사유를 하나의 통합 메시지로 합치지 않는다.
- ❌ 변수가 들어가는 메시지를 `+` 문자열 결합으로 작성하지 않는다.
