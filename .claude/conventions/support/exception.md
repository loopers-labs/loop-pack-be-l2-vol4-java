# 예외 / 에러 응답 컨벤션

## 책임
도메인·서비스 계층의 모든 예외를 단일 `CoreException`으로 통일하고, `ApiControllerAdvice`가 HTTP 응답으로 변환한다. 컨트롤러·서비스에서 직접 HTTP 처리를 하지 않는다.

## 정식 참조
`apps/commerce-api/src/main/java/com/loopers/support/error/CoreException.java`,
`apps/commerce-api/src/main/java/com/loopers/support/error/ErrorType.java`,
`apps/commerce-api/src/main/java/com/loopers/interfaces/api/ApiControllerAdvice.java`,
`apps/commerce-api/src/main/java/com/loopers/interfaces/api/ApiResponse.java`

## 핵심 규칙

### CoreException
- 도메인·서비스 계층은 `CoreException(ErrorType)` 또는 `CoreException(ErrorType, customMessage)`만 던진다.
- `customMessage`가 있으면 기본 메시지(`errorType.getMessage()`) 대신 사용된다.
- 커스텀 예외 클래스를 별도로 만들지 않는다 — `ErrorType`으로 사유를 표현한다.

### ErrorType
| enum 값 | HTTP 상태 | code | 사용 상황 |
|---|---|---|---|
| `BAD_REQUEST` | 400 Bad Request | `"Bad Request"` | 형식 위반, 길이 초과, null 등 입력 오류 |
| `UNAUTHENTICATED` | 401 Unauthorized | `"Unauthorized"` | 인증 실패 (헤더 누락, 비밀번호 불일치 등) |
| `NOT_FOUND` | 404 Not Found | `"Not Found"` | 존재하지 않는 리소스 조회 |
| `CONFLICT` | 409 Conflict | `"Conflict"` | 중복 리소스 (로그인 ID·이메일 중복 등) |
| `INTERNAL_ERROR` | 500 Internal Server Error | `"Internal Server Error"` | 예기치 않은 서버 오류 |

> `UNAUTHENTICATED`는 의미상 인증 실패를 뜻한다. HTTP 상태는 401(`Unauthorized`)이며 code도 reason phrase인 `"Unauthorized"`를 그대로 사용한다. enum 이름이 RFC 7235의 명명(`Unauthorized`)과 의미(인증 실패) 불일치를 보정한다.

### ApiControllerAdvice
- `CoreException`은 `errorType.getStatus()`로 HTTP 상태를, `errorType.getCode()`와 `customMessage`(없으면 `errorType.getMessage()`)로 응답 본문을 결정한다.
- Spring 표준 예외(`MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException`, `HttpMessageNotReadableException`, `ServerWebInputException`, `NoResourceFoundException` 등)도 모두 `ApiResponse.fail(...)`로 변환한다. `HttpMessageNotReadableException`은 근본 원인(`InvalidFormatException`/`MismatchedInputException`/`JsonMappingException`)별로 상세 메시지를 만든다.
- 처리되지 않은 모든 `Throwable`은 `INTERNAL_ERROR`로 떨어진다.
- 컨트롤러·서비스에서 직접 `ResponseEntity`를 만들거나 HTTP 상태를 결정하지 않는다.

### ApiResponse
- 실패 응답은 `ApiResponse.fail(errorCode, errorMessage)` 팩토리를 통해 만든다.
- `meta.result` = `FAIL`, `meta.errorCode` = `errorType.code`, `meta.message` = 에러 메시지.

## 핵심 발췌
```java
// ErrorType — code는 HttpStatus의 reason phrase로 파생(문자열 하드코딩 X)
public enum ErrorType {
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), "일시적인 오류가 발생했습니다."),
    BAD_REQUEST    (HttpStatus.BAD_REQUEST,           HttpStatus.BAD_REQUEST.getReasonPhrase(),           "잘못된 요청입니다."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED,          HttpStatus.UNAUTHORIZED.getReasonPhrase(),          "인증되지 않은 사용자입니다."),
    NOT_FOUND      (HttpStatus.NOT_FOUND,             HttpStatus.NOT_FOUND.getReasonPhrase(),             "존재하지 않는 요청입니다."),
    CONFLICT       (HttpStatus.CONFLICT,              HttpStatus.CONFLICT.getReasonPhrase(),              "이미 존재하는 리소스입니다.");
    ...
}

// 도메인/서비스에서 예외 던지기
throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 로그인 ID입니다.");
throw new CoreException(ErrorType.NOT_FOUND, "회원이 존재하지 않습니다.");

// ApiControllerAdvice 핵심 변환
private ResponseEntity<ApiResponse<?>> failureResponse(ErrorType errorType, String errorMessage) {
    return ResponseEntity.status(errorType.getStatus())
        .body(ApiResponse.fail(
            errorType.getCode(),
            errorMessage != null ? errorMessage : errorType.getMessage()
        ));
}
```

## do / don't
- ✅ 도메인·서비스에서는 `CoreException(ErrorType, message)`만 던진다.
- ✅ 인증 실패의 모든 사유(헤더 누락·포맷 위반·미존재·비밀번호 불일치)는 `UNAUTHENTICATED` 단일 응답으로 통합한다 — user enumeration 방지.
- ✅ `ApiControllerAdvice`가 HTTP 변환을 전담한다.
- ❌ 커스텀 예외 클래스를 별도로 만들지 않는다 — `ErrorType`으로 충분.
- ❌ 컨트롤러·서비스에서 `ResponseEntity`를 직접 만들거나 try-catch로 HTTP 상태를 분기하지 않는다.
- ❌ 새 `ErrorType`의 `code`를 문자열로 하드코딩하지 않는다 — `HttpStatus.*.getReasonPhrase()`로 파생한다.
