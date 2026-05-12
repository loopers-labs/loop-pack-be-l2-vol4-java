# 회원가입 API 명세

## 1. Overview

신규 회원을 시스템에 등록한다. 비인증 API이며, 가입 성공이 곧 로그인 상태를 의미하지 않는다. 이후 인증이 필요한 요청은 별도 헤더(`X-Loopers-LoginId`, `X-Loopers-LoginPw`)로 본인 인증을 수행한다.

## 2. Endpoint

| Method | Path |
|---|---|
| `POST` | `/api/v1/users` |

## 3. Request

### 3.1 Headers

| Name | Required | Value |
|---|---|---|
| `Content-Type` | ✓ | `application/json; charset=utf-8` |

### 3.2 Body — `SignUpRequest`

| Field | Type | Required | 제약 |
|---|---|---|---|
| `loginId` | `string` | ✓ | 영문 대소문자·숫자, 4~20자 |
| `password` | `string` | ✓ | 8~16자, 영문/숫자/특수문자, `birthDate` 포함 불가 |
| `name` | `string` | ✓ | 한글 완성형 2~20자 |
| `birthDate` | `string` (`YYYY-MM-DD`) | ✓ | 유효 달력 일자, 미래 불가 |
| `email` | `string` | ✓ | RFC 5322 호환, 최대 254자 |

### 3.3 예시

```json
{
  "loginId": "kylekim",
  "password": "Kyle!2030",
  "name": "김카일",
  "birthDate": "1995-03-21",
  "email": "kyle@example.com"
}
```

## 4. Response — Success

`201 Created`

### 4.1 Body — `ApiResponse<SignUpResponse>`

| Field | Type | 설명 |
|---|---|---|
| `meta.result` | `string` | 항상 `"SUCCESS"` |
| `meta.errorCode` | `string` \| `null` | 성공 시 `null` |
| `meta.message` | `string` \| `null` | 성공 시 `null` |
| `data.userId` | `number` | 신규 회원 식별자 |
| `data.loginId` | `string` | 신규 회원 로그인 ID |

### 4.2 예시

```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": { "userId": 1, "loginId": "kylekim" }
}
```

## 5. Response — Error

### 5.1 응답 매트릭스

| 상황 | HTTP | `errorCode` | `message` 예 |
|---|---|---|---|
| 필수 필드 누락 | `400` | `Bad Request` | `"loginId는 필수입니다."` |
| `loginId` 패턴·길이 위반 | `400` | `Bad Request` | `"로그인 ID는 영문/숫자 4~20자만 허용됩니다."` |
| `password` 포맷·길이 위반 | `400` | `Bad Request` | `"비밀번호는 8~16자의 영문·숫자·특수문자만 허용됩니다."` |
| `password`에 `birthDate` 포함 | `400` | `Bad Request` | `"비밀번호에 생년월일을 포함할 수 없습니다."` |
| `name` 위반 | `400` | `Bad Request` | `"이름은 한글 2~20자만 허용됩니다."` |
| `birthDate` 위반 | `400` | `Bad Request` | `"생년월일은 유효한 날짜여야 하며 미래일 수 없습니다."` |
| `email` 위반 | `400` | `Bad Request` | `"이메일 형식이 올바르지 않습니다."` |
| `loginId` 중복 | `409` | `Conflict` | `"이미 사용 중인 로그인 ID입니다."` |
| `email` 중복 | `409` | `Conflict` | `"이미 사용 중인 이메일입니다."` |

### 5.2 예시

```json
{
  "meta": { "result": "FAIL", "errorCode": "Conflict", "message": "이미 사용 중인 로그인 ID입니다." },
  "data": null
}
```

### 5.3 비고

- 클라이언트가 사유별로 UI를 분기해야 한다면 `meta`에 별도 `subCode`(`LOGIN_ID_DUPLICATED` 등) 필드 도입을 검토할 수 있다. 본 명세는 기존 `ApiResponse` 컨벤션을 그대로 따르고 `subCode`는 도입하지 않는다.
- 비밀번호 평문은 어떤 응답 본문에도 포함되지 않는다. 위반 사유 메시지에도 비밀번호 원문은 노출하지 않는다.
