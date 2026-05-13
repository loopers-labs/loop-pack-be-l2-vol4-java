# VOLUME-1 SPECIFICATION

이 문서는 커머스 애플리케이션의 회원 관련 요구사항을 기반으로 한 설계 명세서입니다.

## 1. 데이터 모델 설계

### Member (회원)

회원의 기본 정보를 관리하는 엔티티입니다. `BaseEntity`를 상속받아 생성일, 수정일, 삭제일(Soft Delete) 정보를 포함합니다.

| 필드명 | 타입 | 설명 | 제약 조건 |
| :--- | :--- | :--- | :--- |
| `id` | `Long` | 고유 식별자 | PK, Auto Increment |
| `loginId` | `String` | 로그인 ID | Unique, 영문/숫자만 허용 |
| `password` | `String` | 암호화된 비밀번호 | 8~16자, 대소문자/숫자/특수문자 포함 |
| `name` | `String` | 이름 | 한글/영문 |
| `birthDate` | `LocalDate` | 생년월일 | YYYY-MM-DD |
| `email` | `String` | 이메일 | Email 형식 |
| `createdAt` | `ZonedDateTime` | 생성일시 | Not Null |
| `updatedAt` | `ZonedDateTime` | 수정일시 | Not Null |
| `deletedAt` | `ZonedDateTime` | 삭제일시 | Nullable (Soft Delete) |

## 2. 제약 조건 및 비즈니스 로직

### 비밀번호 규칙
- 8~16자의 영문 대소문자, 숫자, 특수문자 조합.
- 생년월일이 비밀번호에 포함될 수 없음.
- 비밀번호 변경 시 현재 비밀번호와 동일한 비밀번호로 변경 불가.

### 회원가입
- 중복된 `loginId`로 가입 불가.
- 이름, 이메일, 생년월일 포맷 검증 필수.

### 내 정보 조회
- 이름의 마지막 글자를 `*`로 마스킹 처리하여 반환.

## 3. API 명세

### 회원가입 (Sign Up)
- **POST** `/v1/members/signup`
- **Request Body**: `{ loginId, password, name, birthDate, email }`

### 내 정보 조회 (Get My Info)
- **GET** `/v1/members/me`
- **Request Header**:
    - `X-Loopers-LoginId`: 로그인 ID
    - `X-Loopers-LoginPw`: 비밀번호
- **Response Body**: `{ loginId, name (masked), birthDate, email }`

### 비밀번호 수정 (Update Password)
- **PATCH** `/v1/members/me/password`
- **Request Header**:
    - `X-Loopers-LoginId`: 로그인 ID
    - `X-Loopers-LoginPw`: 비밀번호
- **Request Body**: `{ oldPassword, newPassword }`
