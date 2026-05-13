# 비밀번호 수정 API 명세

## 1. Overview

본인 인증된 회원의 비밀번호를 새 값으로 교체한다. 매 요청마다 `X-Loopers-LoginId` / `X-Loopers-LoginPw` 헤더로 본인 인증을 수행한 뒤, 본문 `currentPassword`로 변경 의도를 한 번 더 재확인하고, `newPassword`가 비밀번호 RULE을 만족하면 BCrypt 해시로 교체한다. 본 시스템엔 세션·토큰 모델이 없으므로 변경 후 클라이언트는 다음 요청부터 새 비밀번호를 헤더에 사용해야 한다.

## 2. Endpoint

| Method | Path |
|---|---|
| `PATCH` | `/api/v1/users/me/password` |

## 3. Request

### 3.1 Headers

| Name | Required | Value |
|---|---|---|
| `Content-Type` | ✓ | `application/json; charset=utf-8` |
| `X-Loopers-LoginId` | ✓ | 회원 로그인 ID |
| `X-Loopers-LoginPw` | ✓ | 회원의 평문 비밀번호 (변경 전) |

### 3.2 Body — `ChangePasswordRequest`

| Field | Type | Required | 제약 |
|---|---|---|---|
| `currentPassword` | `string` | ✓ | 회원의 저장된 비밀번호와 일치해야 함. 헤더 `X-Loopers-LoginPw`와 별개로 본문에서 한 번 더 검증된다. |
| `newPassword` | `string` | ✓ | 8~16자, 영문 대소문자·숫자·특수문자만 허용, `birthDate` 포함 불가, `currentPassword`와 동일 불가 |

### 3.3 예시

```
PATCH /api/v1/users/me/password
X-Loopers-LoginId: kylekim
X-Loopers-LoginPw: Kyle!2030

{
  "currentPassword": "Kyle!2030",
  "newPassword": "Newer!2031"
}
```

## 4. Response — Success

`200 OK`

### 4.1 Body — `ApiResponse<Void>`

| Field | Type | 설명 |
|---|---|---|
| `meta.result` | `string` | 항상 `"SUCCESS"` |
| `meta.errorCode` | `string` \| `null` | 성공 시 `null` |
| `meta.message` | `string` \| `null` | 성공 시 `null` |
| `data` | `null` | 응답 본문에 데이터 없음 |

### 4.2 예시

```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": null
}
```

## 5. Response — Error

### 5.1 응답 매트릭스

| 상황 | HTTP | `errorCode` | `message` 예 |
|---|---|---|---|
| 본인 인증 실패 (헤더 누락 / 헤더 값 포맷 위반 / 회원 미존재 / 헤더 비밀번호 불일치) | `401` | `Unauthorized` | `"인증되지 않은 사용자입니다."` |
| 필수 필드 누락 | `400` | `Bad Request` | `"비밀번호는 필수입니다."` |
| `currentPassword` 불일치 | `400` | `Bad Request` | `"기존 비밀번호가 일치하지 않습니다."` |
| `newPassword` 포맷·길이 위반 | `400` | `Bad Request` | `"비밀번호는 8~16자의 영문·숫자·특수문자만 허용됩니다."` |
| `newPassword`에 `birthDate` 포함 | `400` | `Bad Request` | `"비밀번호에 생년월일을 포함할 수 없습니다."` |
| `newPassword` == `currentPassword` | `400` | `Bad Request` | `"새 비밀번호는 기존 비밀번호와 동일할 수 없습니다."` |

### 5.2 예시

```json
{
  "meta": { "result": "FAIL", "errorCode": "Bad Request", "message": "기존 비밀번호가 일치하지 않습니다." },
  "data": null
}
```

### 5.3 비고

- 헤더 인증 실패는 사유와 무관하게 단일 응답이다 — 사용자 열거 방지를 위해 상태 코드 / `errorCode` / `message` / 헤더 어디에도 사유 식별 신호를 두지 않는다. 사유 분류는 내부 로그에만 기록한다.
- 본문 `currentPassword` 불일치는 헤더 인증과 별개의 도메인 입력 검증이며 `400 Bad Request`로 처리한다 — 사용자 열거가 일어나지 않는 위치(이미 헤더 인증을 통과한 상태)의 검증이므로 인증 단일 응답 정책의 예외가 아니다.
- 평문 비밀번호(`currentPassword`, `newPassword`, `X-Loopers-LoginPw` 헤더 값)는 어떤 응답 본문에도 포함되지 않으며, 에러 메시지에도 원문은 노출하지 않는다.
