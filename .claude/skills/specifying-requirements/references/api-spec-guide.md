# API 명세 가이드 (단계 7-a)

산출물: `docs/volume-N/{feature}/API_명세.md`

## 표준 구조 (엄격)

```markdown
# {feature} API 명세

## 1. Overview
(2~3줄. 이 명세가 다루는 기능과 범위. 인증 정책도 한 줄로.)

## 2. Endpoint

| Method | Path |
|---|---|
| `METHOD` | `/api/v1/...` |

## 3. Request

### 3.1 Headers
### 3.2 Body — `{X}Request`
### 3.3 예시

## 4. Response — Success

### 4.1 Body — `ApiResponse<{X}Response>`
### 4.2 예시

## 5. Response — Error

### 5.1 응답 매트릭스
### 5.2 예시
### 5.3 비고
```

## 응답 envelope — `ApiResponse<T>` 단일 표준

본 프로젝트(`interfaces/api/ApiResponse.java`)의 envelope 구조를 그대로 사용. **선택지 없음** (이전 가이드의 "선택 A vs 선택 B" 논의는 폐기).

**성공**:
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": { "...": "..." }
}
```

**실패**:
```json
{
  "meta": { "result": "FAIL", "errorCode": "Bad Request", "message": "..." },
  "data": null
}
```

- `meta.result`: `"SUCCESS"` 또는 `"FAIL"`
- `meta.errorCode`: 실패 시 `ErrorType.code`(HttpStatus reasonPhrase, 예: `"Bad Request"`, `"Conflict"`)
- `meta.message`: 실패 시 `CoreException.customMessage`가 그대로

## 에러 매트릭스 — 4-컬럼 표 (엄격)

```markdown
## 5. Response — Error

본 프로젝트의 `ApiResponse.fail(errorCode, message)` 컨벤션을 따른다. `errorCode`는 `ErrorType.code`, `message`는 `CoreException.customMessage`.

### 5.1 응답 매트릭스

| 상황 | HTTP | `errorCode` | `message` 예 |
|---|---|---|---|
| `loginId` 중복 | `409` | `Conflict` | `"이미 사용 중인 로그인 ID입니다."` |
| `password` 위반 | `400` | `Bad Request` | `"비밀번호는 8~16자의 영문·숫자·특수문자 중 2개 이상의 조합이어야 합니다."` |
```

각 행은 **단계 5 시나리오 다이어그램의 응답과 일관**되어야 한다. 둘이 불일치하면 단계 5로 돌아가 확인.

## 비고 섹션 (`### 5.3 비고`)

에러 매트릭스 아래에 결정사항·예외 정책을 적는다.

예:
```markdown
### 5.3 비고

- 클라이언트가 사유별로 UI를 분기해야 한다면 `meta`에 별도 `subCode`(`LOGIN_ID_DUPLICATED` 등) 필드 도입을 검토할 수 있다. 본 명세는 기존 `ApiResponse` 컨벤션을 그대로 따르고 `subCode`는 도입하지 않는다.
- 비밀번호 평문은 어떤 응답 본문에도 포함되지 않는다.
```

## 본 프로젝트 컨벤션

### Controller 클래스
`{X}V1Controller` (예: `UserV1Controller`). 버전 prefix `V1`.

### ApiSpec 인터페이스
OpenAPI 문서화를 위해 `{X}V1ApiSpec` 인터페이스를 둔다 (`springdoc-openapi` 사용). Controller는 이 인터페이스를 `implements`.

### DTO 명명
- 요청: `{X}V1Dto.SignUpRequest` (record). Bean Validation 어노테이션(`@NotBlank`, `@Pattern`, `@Email` 등)으로 1차 검증.
- 응답: `{X}V1Dto.SignUpResponse` (record, `static from(...)` 팩토리).

### URL 컨벤션
- 리소스 컬렉션: `/api/v1/users`
- 단일 리소스: `/api/v1/users/{userId}` (path variable은 camelCase)
- 하위 리소스: `/api/v1/posts/{postId}/likes`
- 본인 컨텍스트: `/api/v1/users/me`
- 버전 prefix `/v1` 일관 사용

### 인증 헤더

본 프로젝트 표준: `X-Loopers-LoginId`, `X-Loopers-LoginPw` (헤더 인증). 인증이 필요한 엔드포인트의 Headers 표에 반드시 명시.

### 빈 필드 / null 정책

- 응답에 등장하지 않을 수 있는 필드는 `Required` 컬럼을 `N`으로 두고 "특정 조건에서만 포함"
- 마스킹/변환된 필드는 "응답 시 마스킹 적용" 명시

## 단계 5와의 동기화

다이어그램의 응답 코드 = 명세의 에러 매트릭스 행. 어느 한쪽만 갱신하지 마라.

## 한 파일에 여러 엔드포인트

`## 6. (다음 엔드포인트)`, `## 7. ...` 형태로 같은 파일에 번호 매겨 나열. 별도 파일로 쪼개지 마라.

## 단계 7-b와의 관계

API의 요청·응답 필드와 ERD의 컬럼은 이름 규칙만 다르고 의미는 같다:
- API: `productId` (camelCase) ↔ DB: `product_id` (snake_case)
- 매핑이 1:1이 아닌 경우(API 단일 필드 ↔ DB 여러 컬럼)는 명세에 명시.
