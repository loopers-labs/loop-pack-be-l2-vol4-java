# 내 정보 조회 API 명세

## 1. Overview

본인 인증된 회원의 정보를 조회한다. 매 요청마다 `X-Loopers-LoginId` / `X-Loopers-LoginPw` 헤더로 본인 인증을 수행한 뒤 회원의 `loginId`, `name`(마스킹), `birthDate`, `email`을 반환한다. 본인의 정보만 반환하며, 다른 회원을 조회할 수단은 제공하지 않는다.

## 2. Endpoint

| Method | Path |
|---|---|
| `GET` | `/api/v1/users/me` |

## 3. Request

### 3.1 Headers

| Name | Required | Value |
|---|---|---|
| `X-Loopers-LoginId` | ✓ | 회원 로그인 ID |
| `X-Loopers-LoginPw` | ✓ | 회원의 평문 비밀번호 |

요청 본문은 없다.

### 3.2 예시

```
GET /api/v1/users/me
X-Loopers-LoginId: kylekim
X-Loopers-LoginPw: Kyle!2030
```

## 4. Response — Success

`200 OK`

### 4.1 Body — `ApiResponse<MyInfoResponse>`

| Field | Type | 설명 |
|---|---|---|
| `meta.result` | `string` | 항상 `"SUCCESS"` |
| `meta.errorCode` | `string` \| `null` | 성공 시 `null` |
| `meta.message` | `string` \| `null` | 성공 시 `null` |
| `data.loginId` | `string` | 회원 로그인 ID |
| `data.name` | `string` | 회원 이름. 마지막 1글자가 `*`로 치환된 마스킹 값. 원본 이름은 응답에 포함되지 않는다. |
| `data.birthDate` | `string` (`YYYY-MM-DD`) | 회원 생년월일 |
| `data.email` | `string` | 회원 이메일 |

### 4.2 예시

```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "loginId": "kylekim",
    "name": "김카*",
    "birthDate": "1995-03-21",
    "email": "kyle@example.com"
  }
}
```

## 5. Response — Error

### 5.1 응답 매트릭스

| 상황 | HTTP | `errorCode` | `message` 예 |
|---|---|---|---|
| 본인 인증 실패 (헤더 누락 / 헤더 값 포맷 위반 / 회원 미존재 / 비밀번호 불일치) | `401` | `Unauthorized` | `"인증되지 않은 사용자입니다."` |

### 5.2 예시

```json
{
  "meta": { "result": "FAIL", "errorCode": "Unauthorized", "message": "인증되지 않은 사용자입니다." },
  "data": null
}
```

### 5.3 비고

- 본인 인증 실패의 사유는 응답 어디(상태 코드 / `errorCode` / `message` / 헤더)에도 구분되어 노출되지 않는다. 사용자 열거 공격을 방지하기 위함이며, 사유 분류는 내부 로그에만 기록한다.
- 평문 비밀번호(`X-Loopers-LoginPw` 헤더 값)는 어떤 응답 본문에도 포함되지 않으며, 에러 메시지에도 비밀번호 원문은 노출하지 않는다.
- 응답 `name` 값은 항상 마스킹된 형태다. 회원가입에서 `name`은 한글 완성형 2~20자로 보장되므로 마스킹 대상 글자가 항상 존재한다.
