# {feature} API 명세

<!-- 사용법: 단계 7-a 결과. 기능에 속한 모든 엔드포인트를 번호 매겨 나열. -->

## 1. Overview

<!-- 2~3줄. 이 명세가 다루는 기능과 범위. 인증 정책(비인증/헤더 인증 등)도 한 줄로. -->

## 2. Endpoint

| Method | Path |
|---|---|
| `METHOD` | `/api/v1/...` |

## 3. Request

### 3.1 Headers

| Name | Required | Value |
|---|---|---|
| `Content-Type` | ✓ | `application/json; charset=utf-8` |

### 3.2 Body — `{X}Request`

| Field | Type | Required | 제약 |
|---|---|---|---|
| | | | |

### 3.3 예시

```json
{ }
```

## 4. Response — Success

`2xx Status`

### 4.1 Body — `ApiResponse<{X}Response>`

| Field | Type | 설명 |
|---|---|---|
| `meta.result` | `string` | 항상 `"SUCCESS"` |
| `meta.errorCode` | `string` \| `null` | 성공 시 `null` |
| `meta.message` | `string` \| `null` | 성공 시 `null` |
| `data.{...}` | | |

### 4.2 예시

```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": { }
}
```

## 5. Response — Error

본 프로젝트의 `ApiResponse.fail(errorCode, message)` 컨벤션을 따른다. `errorCode`는 `ErrorType.code`(HttpStatus reasonPhrase, 예: `"Bad Request"`, `"Conflict"`), `message`는 `CoreException.customMessage`가 그대로 노출된다.

### 5.1 응답 매트릭스

| 상황 | HTTP | `errorCode` | `message` 예 |
|---|---|---|---|
| | | | |

### 5.2 예시

```json
{
  "meta": { "result": "FAIL", "errorCode": "...", "message": "..." },
  "data": null
}
```

### 5.3 비고

<!-- 결정사항·예외 정책. 예: subCode 미도입 결정, 민감 정보 비노출 정책 등. -->

---

<!-- 엔드포인트가 둘 이상이면 여기에 `## 6. (다음 엔드포인트 이름)` 형태로 동일 구조를 반복. 별도 파일로 쪼개지 마라. -->
