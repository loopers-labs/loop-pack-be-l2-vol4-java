# TDD 진행 트래커 — 회원 도메인

> 한 사이클(🔴 RED → 🟢 GREEN → 🔵 REFACTOR)이 끝날 때마다 체크박스를 채운다.
> 새 세션 시작 시 가장 먼저 이 파일을 열어 **현재 위치**를 확인한다.

---

## 📍 현재 위치 (세션 재개용)

- **마지막 작업일:** 2026-05-14
- **현재 위치:** **회원가입 단위 5/5 + 통합 3/3 + E2E 1/2 완주** ✅
- **다음 사이클:** `사이클 10 — 필수 정보 누락 시 400 Bad Request` (🔴 RED 부터)
- **다음 작업:** `UserV1ApiE2ETest.Register` 에 `throwsBadRequest_whenLoginIdIsMissing` 테스트 추가 — `loginId` 누락 요청 → 400 기대
- **마지막 테스트 실행 결과:** 전체 테스트 → **BUILD SUCCESSFUL** ✅

> 새 세션을 열면: 이 파일 + [`TEST_GUIDE.md`](./TEST_GUIDE.md) + [`.codeguide/loopers-1-week.md`](./.codeguide/loopers-1-week.md) 세 개를 먼저 읽고 이어간다.

---

## 🗺️ 전체 로드맵 (총 25 사이클)

```
┌─ 1. 회원가입       (10 사이클)  ── 단위 5 → 통합 3 → E2E 2
├─ 2. 내정보 조회    ( 7 사이클)  ── 단위 1 → 통합 3 → E2E 3
└─ 3. 비밀번호 변경  ( 8 사이클)  ── 단위 3 → 통합 2 → E2E 3
```

각 기능을 **수직 슬라이스** (단위 → 통합 → E2E) 로 끝낸 다음 다음 기능으로 넘어간다.

---

## 1️⃣ 회원가입

### 🧱 단위 테스트 — `domain/user/UserModelTest.java`

**진입 직전 필요 파일:** `domain/user/UserModel.java` (필드 + 생성자만)

> **💡 이 단계의 학습 포인트** — 참고: [`TDD_LEARNING_NOTES.md`](./TDD_LEARNING_NOTES.md) Phase 1~3 + 9
> - **Phase 1** "테스트는 의도의 박제" — 자동 검증으로 회귀를 막는다
> - **Phase 2** "Red 가 먼저인 이유" — 테스트 자체의 신뢰도 검증
>   · **RED 직전 스켈레톤** — 단위 단계에선 "필드 + 생성자 + 호출되는 getter" 만
>   · **"통과해버리는 RED" 함정** — AssertionFailedError 가 아닌 NPE 가 뜨면 시나리오가 잘못된 것
>   · **REFACTOR 에서 할 일** — 상수화, 중복 제거, 이름 개선 (새 기능 X, 테스트 수정 X)
> - **Phase 3** "한 테스트는 한 가지로만 실패" — 5케이스를 따로 쪼개는 이유, "VALID 기본값 + 한 필드만 깨뜨리기" 패턴
> - **Phase 9** "실용 가이드" — AAA 패턴, 명명 규칙, 사이클 시간 5~10분, YAGNI

#### 사이클 1 — loginId 가 영문/숫자 10자 이내가 아니면 BAD_REQUEST
- [x] 🔴 RED — 한글 ID 로 생성 시도, BAD_REQUEST 기대 테스트 _(2026-05-12, AssertionFailedError 로 실패 확인)_
- [x] 🟢 GREEN — 생성자에 loginId 정규식 검증 추가 _(2026-05-12, `loginId.matches("^[A-Za-z0-9]{1,10}$")` 인라인)_
- [x] 🔵 REFACTOR — 정규식 상수 분리, 검증 메서드 추출 _(2026-05-12, `LOGIN_ID_PATTERN` static final + `validateLoginId(...)` 메서드)_

#### 사이클 2 — 이메일이 `xx@yy.zz` 형식이 아니면 BAD_REQUEST
- [x] 🔴 RED — 잘못된 이메일로 생성 시도, BAD_REQUEST 기대 테스트 _(2026-05-12, AssertionFailedError 로 실패 확인)_
- [x] 🟢 GREEN — 생성자에 이메일 정규식 검증 추가 _(2026-05-12, 인라인 정규식)_
- [x] 🔵 REFACTOR — 정규식 상수화, 검증 헬퍼 일관성 _(2026-05-12, `EMAIL_PATTERN` static final + `validateEmail(...)` 메서드, 사이클 1 과 같은 패턴)_

#### 사이클 3 — 생년월일이 `yyyy-MM-dd` 형식이 아니면 BAD_REQUEST
- [x] 🔴 RED — 형식 안 맞는 생년월일로 생성 시도, BAD_REQUEST 기대 _(2026-05-12, "1990/01/01" 사용 — AssertionFailedError 로 실패 확인)_
- [x] 🟢 GREEN — 정규식 (`^\d{4}-\d{2}-\d{2}$`) 인라인 검증 추가 _(2026-05-12, 명세가 형식 검증까지만 요구하므로 DateTimeFormatter 미도입 — YAGNI)_
- [x] 🔵 REFACTOR — `BIRTH_PATTERN` static final + `validateBirth(...)` 추출 _(2026-05-12, 사이클 1, 2 와 같은 패턴으로 일관성 확보)_

#### 사이클 4 — 비밀번호 RULE (8~16자 영문/숫자/특수) 위반 시 BAD_REQUEST
- [x] 🔴 RED — `"abc1!"` (5자) 로 생성 시도, AssertionFailedError 확인 _(2026-05-13)_
- [x] 🟢 GREEN — 인라인 정규식 검증 추가 _(2026-05-13)_
- [x] 🔵 REFACTOR — `validatePass(...)` 메서드 추출 + `PASSWORD_PATTERN` 상수화 _(2026-05-13)_

#### 사이클 5 — 비밀번호에 생년월일이 포함되면 BAD_REQUEST
- [x] 🔴 RED — `"ab1990-01-01"` 로 생성 시도 (형식은 통과하나 birth 포함), RED 확인 _(2026-05-13)_
- [x] 🟢 GREEN + 🔵 REFACTOR — `validatePass(password, birth)` 로 시그니처 확장, 내부에 `password.contains(birth)` 검증 추가 _(2026-05-13, A안 — 비밀번호 RULE 한 메서드에 응집)_

---

### 🔗 통합 테스트 — `domain/user/UserServiceIntegrationTest.java`

**진입 직전 필요 파일:**
- `domain/user/UserRepository.java` (인터페이스)
- `infrastructure/user/UserJpaRepository.java`
- `infrastructure/user/UserRepositoryImpl.java`
- `domain/user/UserService.java` (메서드 시그니처만)
- `domain/user/PasswordEncoder.java` (인터페이스)
- `infrastructure/user/BCryptPasswordEncoderAdapter.java`

> **💡 이 단계의 학습 포인트** — 참고: [`TDD_LEARNING_NOTES.md`](./TDD_LEARNING_NOTES.md) Phase 4~6 + 9
> - **Phase 4** "테스트 더블 5형제" — 의존성을 가짜로 바꿔치기 (Dummy/Stub/Mock/Spy/Fake)
> - **Phase 5** "Mockito 는 도구, Mock 은 개념" — 같은 객체에 Stub + Mock 역할 동시 부여 가능
> - **Phase 6** "단위는 내 로직, 통합은 내 가정을 검증" — JPA save 가 진짜 INSERT 를 날리는지, 트랜잭션이 의도대로 잡히는지 확인하는 단계
> - **Phase 9** "테스트 더블 결정 트리" — 호출 검증이면 Mock/Spy, 반환값이 전제면 Stub/Fake. PasswordEncoder 는 Fake 가 정답 (BCrypt 비결정성 제거)

#### 사이클 6 — User 저장이 수행된다 (spy 검증)
- [x] 🔴 RED — `userService.register()` 호출 후 `userRepository.save()` 호출 검증 (Spy) _(2026-05-13, "Wanted but not invoked" 로 실패 확인)_
- [x] 🟢 GREEN — `UserService.register` 에서 `userRepository.save(user)` 호출 _(2026-05-13)_
- [x] 🔵 REFACTOR — 생략 (한 줄짜리라 리팩터 불필요. `@Transactional` 명시는 그대로 유지)
- _학습 메모: 통합 테스트의 정석은 진짜 DB 조회 검증 (Classicist). 사이클 6 은 Phase 4~5 의 Spy 도구 학습 목적. 사이클 7~ 부터 Classicist 로 전환._

#### 사이클 7 — 중복 loginId 로 가입 시 CONFLICT
- [x] 🔴 RED — 같은 loginId 로 두 번 register, AssertionFailedError 확인 _(2026-05-13, 두 번째 INSERT 도 그냥 통과해서 CoreException 안 던져짐 — Classicist 진짜 DB 검증으로 전환)_
- [x] 🟢 GREEN — `UserRepository.existsByLoginId` 추가 + 위임 + `register` 사전 체크 _(2026-05-13)_
- [x] 🔵 REFACTOR — 생략 (체크 → save 흐름이 자연스러움, `@Transactional` 로 한 트랜잭션 보장)
- _학습 메모: Service 의 existsByLoginId 체크 + DB unique 제약 = 두 층의 방어. 현재는 Service 만. race condition 방어는 별도 학습으로._

#### 사이클 8 — 비밀번호가 암호화되어 저장된다
- [ ] 🔴 RED — 가입 후 저장된 비밀번호가 평문과 다른지 검증
- [ ] 🟢 GREEN — `user.encodePassword(passwordEncoder)` 호출 후 저장
- [ ] 🔵 REFACTOR — 인코더 주입 방식, 빈 위치 정리

---

### 🌐 E2E 테스트 — `interfaces/api/UserV1ApiE2ETest.java`

**진입 직전 필요 파일:**
- `interfaces/api/user/UserV1Dto.java`
- `interfaces/api/user/UserV1ApiSpec.java`
- `interfaces/api/user/UserV1Controller.java` (빈 메서드)
- `application/user/UserFacade.java` (시그니처만)
- `application/user/UserInfo.java`

> **💡 이 단계의 학습 포인트** — 참고: [`TDD_LEARNING_NOTES.md`](./TDD_LEARNING_NOTES.md) Phase 7
> - **Phase 7** "통합은 내부, E2E 는 외부 계약" — 통합 통과해도 잡히지 않는 것들:
>   · JSON 직렬화 (`loginId` vs `login_id`)
>   · HTTP 상태 코드 매핑 (`CoreException(CONFLICT)` → 409)
>   · 헤더 처리 (`X-Loopers-LoginId`)
>   · URL/메서드 매핑 (`POST /api/v1/users`)

#### 사이클 9 — 가입 성공 시 200 + 유저 정보 반환 (비밀번호 미포함)
- [x] 🔴 RED — `POST /api/v1/users` → 200 + loginId 반환 + password 필드 부재 _(2026-05-14, NPE 로 실패 확인 — 컨트롤러 null 반환)_
- [x] 🟢 GREEN — Controller → Facade → Service 연결, Response 에서 password 제외 _(2026-05-14, RegisterResponse 에 password 필드 없음 = 컴파일 수준 보장)_
- [x] 🔵 REFACTOR — 생략 (흐름 단순·명확, 새 기능 없음) _(2026-05-14)_

#### 사이클 10 — 필수 정보 누락 시 400 Bad Request
- [ ] 🔴 RED — `loginId` 누락 요청 → 400 기대
- [ ] 🟢 GREEN — `@Valid` + `@NotBlank` / `@NotNull` 적용
- [ ] 🔵 REFACTOR — `ApiControllerAdvice` 의 매핑 확인

---

## 2️⃣ 내 정보 조회

> **💡 두 번째 기능 — 패턴 굳히기**
> 회원가입에서 단위 → 통합 → E2E 흐름을 한 번 체험했다. 같은 패턴을 반복하면서 *피라미드의 비율 감각* (Phase 8) 을 익힌다.
> 내 정보 조회는 단위 1 + 통합 3 + E2E 3 — **단위가 적은 이유**는 검증 규칙이 "이름 마스킹" 하나뿐이기 때문. 도메인 규칙이 적으면 단위 테스트도 자연스럽게 적어진다.

### 🧱 단위 테스트 — `UserModelTest` 의 `MaskedName` 그룹

#### 사이클 11 — 이름의 마지막 글자를 `*` 로 마스킹
- [ ] 🔴 RED — `user.getMaskedName()` 호출 시 마지막 글자가 `*` 기대
- [ ] 🟢 GREEN — `UserModel.getMaskedName()` 구현
- [ ] 🔵 REFACTOR — 1글자/2글자 경계 처리 점검

---

### 🔗 통합 테스트 — `UserServiceIntegrationTest` 의 `FindByLoginId` 그룹

#### 사이클 12 — 회원 존재 시 정보 반환
- [ ] 🔴 RED — 가입 후 `findByLoginId` 호출 → 회원 반환 기대
- [ ] 🟢 GREEN — `UserService.findByLoginId` 구현
- [ ] 🔵 REFACTOR — Repository 조회 시그니처 정리

#### 사이클 13 — 회원 부재 시 `null` 반환
- [ ] 🔴 RED — 존재하지 않는 loginId 로 조회 → null 기대
- [ ] 🟢 GREEN — `Optional.orElse(null)` 처리
- [ ] 🔵 REFACTOR — null 반환 정책 명문화

#### 사이클 14 — 반환되는 이름이 마스킹되어 있음
- [ ] 🔴 RED — 조회 후 `getMaskedName()` 결과 검증
- [ ] 🟢 GREEN — (사이클 11 의 메서드로 이미 동작)
- [ ] 🔵 REFACTOR — Info 객체에서 마스킹 적용 위치 결정

---

### 🌐 E2E 테스트 — `UserV1ApiE2ETest` 의 `GetMyInfo` 그룹

#### 사이클 15 — 올바른 헤더로 조회 시 200 + 마스킹된 정보
- [ ] 🔴 RED — `GET /api/v1/users/me` + 헤더 → 200 + 마스킹 이름 기대
- [ ] 🟢 GREEN — Controller `@RequestHeader` + Facade 인증 분기
- [ ] 🔵 REFACTOR — Facade 의 인증 책임 정리

#### 사이클 16 — 필수 헤더 누락 시 400
- [ ] 🔴 RED — 헤더 없이 요청 → 400 기대
- [ ] 🟢 GREEN — `@RequestHeader(required = true)` 또는 Advice 매핑
- [ ] 🔵 REFACTOR — 누락 vs 잘못된 값 분기 정리

#### 사이클 17 — 비밀번호 불일치 시 401
- [ ] 🔴 RED — 잘못된 비밀번호로 조회 → 401 기대
- [ ] 🟢 GREEN — Facade 에서 `passwordEncoder.matches` 실패 시 UNAUTHORIZED
- [ ] 🔵 REFACTOR — 인증 로직 위치 (Facade vs Service) 점검

---

## 3️⃣ 비밀번호 변경

> **💡 세 번째 기능 — 도메인 메서드의 TDD**
> 지금까지는 *생성자* 검증이었다면, 이번엔 *행위 메서드* (`changePassword`) 의 검증이다.
> 통합 테스트에서 **Fake PasswordEncoder** 의 진가가 드러난다 — BCrypt 의 비결정성을 빼고 검증 흐름만 격리하는 방법. (Phase 4 의 Fake 다시 보기)

### 🧱 단위 테스트 — `UserModelTest` 의 `ChangePassword` 그룹

#### 사이클 18 — 새 비밀번호가 RULE 위반 시 BAD_REQUEST
- [ ] 🔴 RED — 너무 짧은 새 비밀번호로 `changePassword` → BAD_REQUEST 기대
- [ ] 🟢 GREEN — `UserModel.changePassword` 에서 정규식 검증
- [ ] 🔵 REFACTOR — 생성자 검증과 패턴 공유

#### 사이클 19 — 새 비밀번호에 생년월일 포함 시 BAD_REQUEST
- [ ] 🔴 RED — 생년월일 포함 새 비밀번호 → BAD_REQUEST 기대
- [ ] 🟢 GREEN — `contains(birth)` 검증
- [ ] 🔵 REFACTOR — 검증 로직 중복 제거

#### 사이클 20 — 새 비밀번호가 현재 비밀번호와 동일 시 BAD_REQUEST
- [ ] 🔴 RED — 같은 비밀번호로 변경 시도 → BAD_REQUEST 기대
- [ ] 🟢 GREEN — `encoder.matches(new, this.password)` 비교
- [ ] 🔵 REFACTOR — Fake PasswordEncoder 활용 점검

---

### 🔗 통합 테스트 — `UserServiceIntegrationTest` 의 `ChangePassword` 그룹

#### 사이클 21 — 기존 비밀번호 불일치 시 UNAUTHORIZED
- [ ] 🔴 RED — 잘못된 기존 비밀번호로 변경 → UNAUTHORIZED 기대
- [ ] 🟢 GREEN — `UserService.changePassword` 에서 matches 검증
- [ ] 🔵 REFACTOR — 인증 실패 코드 일관성

#### 사이클 22 — 정상 변경 시 DB password 가 새 값으로 암호화되어 갱신
- [ ] 🔴 RED — 변경 후 재조회, 비밀번호가 새 값과 matches 통과
- [ ] 🟢 GREEN — `@Transactional` + `user.changePassword(newPw, encoder)`
- [ ] 🔵 REFACTOR — dirty checking 흐름 점검

---

### 🌐 E2E 테스트 — `UserV1ApiE2ETest` 의 `ChangePassword` 그룹

#### 사이클 23 — 정상 변경 시 200
- [ ] 🔴 RED — `PUT /api/v1/users/me/password` → 200 기대
- [ ] 🟢 GREEN — Controller `@PutMapping` + Facade 연결
- [ ] 🔵 REFACTOR — Request DTO 명확화

#### 사이클 24 — 기존 비밀번호 불일치 시 401
- [ ] 🔴 RED — 잘못된 기존 비밀번호 요청 → 401 기대
- [ ] 🟢 GREEN — UNAUTHORIZED → 401 매핑 확인
- [ ] 🔵 REFACTOR — Advice 매핑 일관성

#### 사이클 25 — 새 비밀번호 RULE 위반 시 400
- [ ] 🔴 RED — RULE 위반 새 비밀번호 → 400 기대
- [ ] 🟢 GREEN — BAD_REQUEST → 400 매핑 확인
- [ ] 🔵 REFACTOR — 도메인 예외 ↔ HTTP 매핑 표 정리

---

## ✅ 매 사이클 끝날 때 자문

- [ ] **RED**: 정말 빨갛게 실패했나? (예외 미발생 / AssertionError)
- [ ] **GREEN**: 방금 테스트뿐 아니라 **이전 모든 테스트도** 통과하나?
- [ ] **REFACTOR**: 새 기능을 끼워넣지 않았나? 모든 테스트 여전히 통과하나?
- [ ] 진행 트래커(이 파일)의 체크박스 업데이트했나?

---

## 📚 세션 재개 시 읽을 파일

1. **이 파일** (`TDD_PROGRESS.md`) — 어디까지 했는지 확인
2. [`TDD_LEARNING_NOTES.md`](./TDD_LEARNING_NOTES.md) — *왜 이렇게 하는가* 8단계 학습 노트 (사이클 진행 중 막힐 때 참조)
3. [`TEST_GUIDE.md`](./TEST_GUIDE.md) — 테스트 종류, 테스트 더블, AAA 패턴, 명명 규칙
4. [`.codeguide/loopers-1-week.md`](./.codeguide/loopers-1-week.md) — 기능 명세 (요구조건)
5. [`CLAUDE.md`](./CLAUDE.md) — 프로젝트 컨벤션
