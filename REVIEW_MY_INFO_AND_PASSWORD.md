# 내 정보 조회 / 비밀번호 변경 — 내일 검증 체크리스트

> 작성일: 2026-05-14  
> 목적: 오늘 탑다운 TDD 로 구현한 두 기능을 내일 직접 손으로 확인하기 위한 기록

---

## 1️⃣ 구현된 API 목록

| HTTP | URL | 설명 |
|------|-----|------|
| GET | `/api/v1/users/me` | 내 정보 조회 (이름 마스킹) |
| PUT | `/api/v1/users/me/password` | 비밀번호 변경 |

---

## 2️⃣ GET /api/v1/users/me — 내 정보 조회

### 요청 형식

```http
GET /api/v1/users/me
X-Loopers-LoginId: testuser
X-Loopers-LoginPw: Password@1
```

### 성공 응답 (200)

```json
{
  "result": "SUCCESS",
  "data": {
    "id": 1,
    "loginId": "testuser",
    "name": "홍길*",
    "birth": "1990-01-01",
    "email": "test@loopers.com"
  }
}
```

> **확인 포인트:** `name` 이 "홍길동" 이 아닌 "홍길*" 로 마스킹되어 있는지

### 실패 시나리오

| 시나리오 | 조건 | 기대 HTTP |
|---------|------|-----------|
| `X-Loopers-LoginId` 헤더 누락 | 헤더 없이 요청 | **400** Bad Request |
| 비밀번호 불일치 | `X-Loopers-LoginPw` 값 틀림 | **401** Unauthorized |
| 존재하지 않는 loginId | 미가입 ID 로 요청 | **401** Unauthorized |

---

## 3️⃣ PUT /api/v1/users/me/password — 비밀번호 변경

### 요청 형식

```http
PUT /api/v1/users/me/password
X-Loopers-LoginId: testuser
Content-Type: application/json

{
  "currentPassword": "Password@1",
  "newPassword": "NewPass@99"
}
```

> **주의:** 이 엔드포인트는 `X-Loopers-LoginPw` 헤더를 사용하지 않는다.  
> 현재 비밀번호 인증은 요청 바디의 `currentPassword` 로 한다.

### 성공 응답 (200)

```json
{
  "result": "SUCCESS",
  "data": null
}
```

### 실패 시나리오

| 시나리오 | 조건 | 기대 HTTP |
|---------|------|-----------|
| 현재 비밀번호 불일치 | `currentPassword` 틀림 | **401** Unauthorized |
| 새 비밀번호 형식 위반 | 8자 미만 등 | **400** Bad Request |
| 새 비밀번호에 생년월일 포함 | `newPassword` 에 birth 포함 | **400** Bad Request |
| 새 비밀번호 = 현재 비밀번호 | 동일 비밀번호로 변경 시도 | **400** Bad Request |

---

## 4️⃣ 내일 수동 검증 순서 (로컬 서버 띄운 후)

```bash
# 인프라 먼저
docker-compose -f ./docker/infra-compose.yml up -d
```

### Step 1 — 회원 가입
```http
POST /api/v1/users
Content-Type: application/json

{
  "loginId": "testuser",
  "password": "Password@1",
  "name": "홍길동",
  "birth": "1990-01-01",
  "email": "test@loopers.com"
}
```

### Step 2 — 내 정보 조회 (성공)
```http
GET /api/v1/users/me
X-Loopers-LoginId: testuser
X-Loopers-LoginPw: Password@1
```
→ `name: "홍길*"` 확인

### Step 3 — 헤더 누락 (400)
```http
GET /api/v1/users/me
```
→ 400 확인

### Step 4 — 비밀번호 불일치 (401)
```http
GET /api/v1/users/me
X-Loopers-LoginId: testuser
X-Loopers-LoginPw: wrongpassword
```
→ 401 확인

### Step 5 — 비밀번호 변경 (성공)
```http
PUT /api/v1/users/me/password
X-Loopers-LoginId: testuser
Content-Type: application/json

{
  "currentPassword": "Password@1",
  "newPassword": "NewPass@99"
}
```
→ 200 확인

### Step 6 — 변경 후 구 비밀번호로 조회 시 401
```http
GET /api/v1/users/me
X-Loopers-LoginId: testuser
X-Loopers-LoginPw: Password@1
```
→ 401 확인 (비밀번호가 바뀌었으므로)

### Step 7 — 변경 후 새 비밀번호로 조회 성공
```http
GET /api/v1/users/me
X-Loopers-LoginId: testuser
X-Loopers-LoginPw: NewPass@99
```
→ 200 + `name: "홍길*"` 확인

### Step 8 — 동일 비밀번호로 변경 시도 (400)
```http
PUT /api/v1/users/me/password
X-Loopers-LoginId: testuser
Content-Type: application/json

{
  "currentPassword": "NewPass@99",
  "newPassword": "NewPass@99"
}
```
→ 400 확인

---

## 5️⃣ 오늘 구현한 파일 목록 (변경된 파일)

### 도메인 계층
| 파일 | 추가 내용 |
|------|-----------|
| `domain/user/UserModel.java` | `getMaskedName()`, `changePassword(newPw, encoder)` |
| `domain/user/UserRepository.java` | `findByLoginId(String)` 인터페이스 |
| `domain/user/UserService.java` | `findByLoginId()`, `changePassword()` |

### 인프라 계층
| 파일 | 추가 내용 |
|------|-----------|
| `infrastructure/user/UserJpaRepository.java` | `findByLoginId()` 파생 쿼리 |
| `infrastructure/user/UserRepositoryImpl.java` | `findByLoginId()` 위임 |

### 응용 계층
| 파일 | 추가 내용 |
|------|-----------|
| `application/user/UserInfo.java` | `maskedName` 필드 추가 |
| `application/user/UserFacade.java` | `getMyInfo()`, `changePassword()`, PasswordEncoder 주입 |

### 인터페이스 계층
| 파일 | 추가 내용 |
|------|-----------|
| `interfaces/api/user/UserV1Dto.java` | `MyInfoResponse`, `ChangePasswordRequest` |
| `interfaces/api/user/UserV1ApiSpec.java` | `getMyInfo()`, `changePassword()` 명세 |
| `interfaces/api/user/UserV1Controller.java` | `GET /me`, `PUT /me/password` 엔드포인트 |

### 테스트
| 파일 | 추가 내용 |
|------|-----------|
| `UserModelTest.java` | `MaskedName`, `ChangePassword` 중첩 클래스 (사이클 11, 18~20) |
| `UserServiceIntegrationTest.java` | `FindByLoginId`, `ChangePassword` 중첩 클래스 (사이클 12~14, 21~22) |
| `UserV1ApiE2ETest.java` | `GetMyInfo`, `ChangePassword` 중첩 클래스 (사이클 15~17, 23~25) |

---

## 6️⃣ 설계 결정 — 내일 다시 보면 좋을 것들

### 인증 책임 분리
- **GET /me** → Facade 에서 `passwordEncoder.matches()` 직접 호출
- **PUT /me/password** → Service 내부에서 `passwordEncoder.matches()` 검증 후 `changePassword()`

### 이름 마스킹 위치
```
UserModel.getMaskedName()          ← 도메인 책임 (마스킹 규칙)
    └→ UserInfo.maskedName         ← 응용 계층 전달
        └→ MyInfoResponse.name     ← 인터페이스 계층 노출
```

### 비밀번호 변경 PUT 헤더 정책
- `X-Loopers-LoginId` : 필수 (누락 시 400)
- `X-Loopers-LoginPw` : 이 엔드포인트에서는 **불필요** — 인증은 바디 `currentPassword` 로

### Dirty Checking
`UserService.changePassword` 에서 `userRepository.save()` 를 명시적으로 부르지 않아도  
`@Transactional` + JPA dirty checking 으로 UPDATE 가 자동 발생한다.

---

## 7️⃣ 테스트 실행 명령어

```bash
# 단위 테스트만
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.user.UserModelTest"

# 통합 테스트만
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.user.UserServiceIntegrationTest"

# E2E 테스트만
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.UserV1ApiE2ETest"

# 전체
./gradlew :apps:commerce-api:test
```
