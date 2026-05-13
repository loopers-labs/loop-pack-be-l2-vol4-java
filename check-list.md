# 회원(User) 도메인 기능 체크리스트

## 1. 도메인 모델

### 1-1. `LoginId`
- [x] null/blank → `BAD_REQUEST`
- [x] 영문/숫자 외 문자 포함 → `BAD_REQUEST`
- [x] 영문만 / 숫자만 / 영문+숫자 조합 → 허용

### 1-2. `Email`
- [x] null/blank → `BAD_REQUEST`
- [x] 포맷 위반 → `BAD_REQUEST`
- [x] 정상 포맷 → 허용

### 1-3. `BirthDate`
- [x] null → `BAD_REQUEST`
- [x] 미래 일자 → `BAD_REQUEST`
- [x] 과거/오늘 일자 → 허용

### 1-4. `Name`
- [x] null/blank → `BAD_REQUEST`
- [x] `masked()` — 마지막 글자를 `*` 로 치환
  - [x] `홍길동` → `홍길*`
  - [x] `Kim` → `Ki*`
  - [x] `A` → `*`

### 1-5. `PasswordPolicy`
- [x] null/빈 값 / 길이 7자 / 17자 → `BAD_REQUEST`
- [x] 허용 외 문자 포함(한글, 공백, 탭) → `BAD_REQUEST`
- [x] `BirthDate` 가 비밀번호 substring 으로 포함 → `BAD_REQUEST`
  - [x] `yyMMdd`
  - [x] `yyyyMMdd`
  - [x] `yy-MM-dd`, `yyyy-MM-dd`
  - [x] `yy/MM/dd`, `yyyy/MM/dd`
  - [x] `yy.MM.dd`, `yyyy.MM.dd`
- [x] 영문 대/소문자 + 숫자 + 특수문자 조합 8~16자 + 생년월일 substring 미포함 → 허용

### 1-6. `UserModel`
- [x] 생성 시 값 객체 위임 검증
- [x] `encodedPassword` null/blank → `BAD_REQUEST`
- [x] 비밀번호는 해시된 값으로 저장
- [x] `changeEncodedPassword(newEncoded)` — 해시 교체 (null/blank 거부)

---

## 2. 회원가입 (`POST /api/v1/users`)

### 2-1. `UserService.signUp`
- [x] 정상 가입 시 영속화 후 반환
- [x] 이미 존재하는 `loginId` → `CONFLICT`
- [x] 비밀번호는 평문이 저장되지 않음 (인코딩 후 저장)
- [x] 비밀번호 정책 위반 시 `BAD_REQUEST`

### 2-2. Controller / Facade
- [x] 정상 요청 → 201 + 응답 DTO (이름 마스킹)
- [x] `loginId` 영문/숫자 외 → 400
- [x] `email` 포맷 위반 → 400
- [x] `birthDate` 미래/포맷 위반 → 400
- [x] `password` 정책 위반 → 400
- [x] 필수 필드 누락 → 400
- [x] 중복 `loginId` → 409

---

## 3. 내 정보 조회 (`GET /api/v1/users/me`)

### 3-1. 인증
- [ ] 헤더 누락 → 401
- [ ] 존재하지 않는 `loginId` → 401
- [ ] 비밀번호 불일치 → 401

### 3-2. 응답
- [ ] 정상 인증 시 이름은 마지막 글자가 `*` 로 마스킹되어 반환
- [ ] 비밀번호는 응답에 포함되지 않음

---

## 4. 비밀번호 수정 (`PATCH /api/v1/users/me/password`)

### 4-1. 인증
- [ ] 헤더 누락/잘못된 자격 → 401

### 4-2. 검증
- [ ] `currentPassword` 불일치 → 400
- [ ] `newPassword` 가 정책 위반 → 400
- [ ] `newPassword == currentPassword` → 400
- [ ] 정상 변경 후 기존 자격으로 재요청 → 401

---

## 5. 통합 / E2E

- [ ] `@SpringBootTest` + Testcontainers(MySQL) 골든 패스: 회원가입 → 조회 → 비밀번호 변경 → (구 자격 401, 신 자격 200)
- [x] 회원가입 중복 ID → 409
- [x] 회원가입 정책 위반 비밀번호(생년월일 포함, 길이 위반, 허용 외 문자) → 400
- [x] 회원가입 잘못된 email / birthDate / loginId → 400
- [ ] 내 정보 조회 헤더 누락 / 미존재 ID / 비밀번호 불일치 → 401
- [ ] 비밀번호 변경 현재 비밀번호 불일치 → 400
- [ ] 비밀번호 변경 신규 == 현재 → 400
- [ ] 비밀번호 변경 신규 정책 위반 → 400
