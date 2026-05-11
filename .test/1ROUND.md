# 1ROUND 기능 정의

## 작성 기준

- 기준 문서: `.note/1주차요구사항.md`
- 테스트 규칙: `.note/테스트코드.md`
- 이번 라운드에서는 기존 구현 코드를 기준으로 삼지 않는다.
- 먼저 요구사항에서 구현할 기능과 규칙을 정리하고, 이후 TFD 방식으로 실패 테스트를 작성한다.
- 이번 구현 범위는 단위 테스트와 통합 테스트로 제한하고, E2E 테스트는 작성하지 않는다.

## 기능적 요구사항

### 1. 회원가입

회원가입 시 필요한 정보는 다음과 같다.

- 로그인 ID
- 비밀번호
- 이름
- 생년월일
- 이메일

회원가입은 다음 요구사항을 만족해야 한다.

- 이미 가입된 로그인 ID로는 가입할 수 없다.
- 각 정보는 포맷에 맞게 검증해야 한다.
- 로그인 ID는 영문과 숫자만 허용한다.
- 비밀번호는 8~16자의 영문 대소문자, 숫자, 특수문자만 허용한다.
- 비밀번호 특수문자는 `!@#$%^&*()`만 허용한다.
- 비밀번호에는 생년월일이 포함될 수 없다.
- 이메일은 `user@email.com`, `user@email.net` 같은 일반적인 `local@domain.tld` 형식만 허용한다.
- 이름, 이메일은 null 또는 blank일 수 없다.
- 생년월일은 null일 수 없다.

### 2. 내 정보 조회

회원가입 이후 유저 정보가 필요한 요청은 공통 요청 헤더를 사용한다.

- `X-Loopers-LoginId`: 로그인 ID
- `X-Loopers-LoginPw`: 비밀번호

내 정보 조회 시 반환할 정보는 다음과 같다.

- 로그인 ID
- 이름
- 생년월일
- 이메일

내 정보 조회 요구사항은 다음과 같다.

- 이름은 마지막 글자를 마스킹해서 반환한다.
- 마스킹 문자는 `*`로 통일한다.

### 3. 비밀번호 수정

비밀번호 수정 시 필요한 정보는 다음과 같다.

- 기존 비밀번호
- 새 비밀번호

비밀번호 수정 요구사항은 다음과 같다.

- 새 비밀번호는 비밀번호 규칙을 따라야 한다.
- 새 비밀번호는 현재 비밀번호와 같을 수 없다.
- 비밀번호는 8~16자의 영문 대소문자, 숫자, 특수문자만 허용한다.
- 비밀번호 특수문자는 `!@#$%^&*()`만 허용한다.
- 비밀번호에는 생년월일이 포함될 수 없다.
- 기존 비밀번호는 `X-Loopers-LoginPw` 헤더로 전달하고, 요청 본문에는 새 비밀번호만 전달한다.

## UserService 단위 테스트 목록

UserService 단위 테스트는 Spring Context 없이 작성한다. 저장소 의존성은 Fake Repository를 우선 사용하고, 테스트는 상태와 반환 결과를 검증한다.

### Red Phase 작성 목록

Red Phase에서는 구현을 보지 않고 요구사항을 만족하지 못하면 실패해야 하는 테스트를 먼저 작성한다. 한 번에 모든 테스트를 작성하지 않고, 기능 흐름을 가장 작게 나눠 실패를 확인한다.

#### 1차 Red: 회원가입 핵심 흐름

- `registersUser_whenUserInfoIsValid`
  - 유효한 정보로 회원가입하면 사용자가 등록되어야 한다.
- `throwsAlreadyExists_whenLoginIdAlreadyExists`
  - 이미 가입된 로그인 ID로 회원가입하면 실패해야 한다.

#### 2차 Red: 회원가입 입력 검증

- `throwsInvalidLoginId_whenLoginIdContainsInvalidCharacters`
  - 로그인 ID에 영문/숫자 외 문자가 포함되면 실패해야 한다.
- `throwsInvalidPassword_whenPasswordLengthIsTooShort`
  - 비밀번호가 8자 미만이면 실패해야 한다.
- `throwsInvalidPassword_whenPasswordLengthIsTooLong`
  - 비밀번호가 16자를 초과하면 실패해야 한다.
- `throwsInvalidPassword_whenPasswordContainsUnsupportedCharacter`
  - 비밀번호가 허용되지 않은 문자를 포함하면 실패해야 한다.
- `throwsInvalidPassword_whenPasswordContainsUnsupportedSpecialCharacter`
  - 비밀번호가 `!@#$%^&*()` 외 특수문자를 포함하면 실패해야 한다.
- `throwsInvalidPassword_whenPasswordContainsBirthDate`
  - 비밀번호가 생년월일을 포함하면 실패해야 한다.
- `throwsUserNameRequired_whenNameIsNull`
  - 이름이 null이면 실패해야 한다.
- `throwsUserNameRequired_whenNameIsBlank`
  - 이름이 blank이면 실패해야 한다.
- `throwsBirthDateRequired_whenBirthDateIsNull`
  - 생년월일이 null이면 실패해야 한다.
- `throwsEmailRequired_whenEmailIsNull`
  - 이메일이 null이면 실패해야 한다.
- `throwsEmailRequired_whenEmailIsBlank`
  - 이메일이 blank이면 실패해야 한다.
- `throwsInvalidEmail_whenEmailFormatIsInvalid`
  - 이메일이 일반적인 `local@domain.tld` 형식이 아니면 실패해야 한다.

#### 3차 Red: 내 정보 조회

- `returnsMyInfo_whenLoginHeadersAreValid`
  - 로그인 ID와 비밀번호가 일치하면 내 정보를 반환해야 한다.
- `returnsMaskedName_whenMyInfoIsReturned`
  - 내 정보 조회 결과의 이름은 마지막 글자가 `*`로 마스킹되어야 한다.
- `throwsNotFound_whenLoginIdDoesNotExist`
  - 가입되지 않은 로그인 ID로 조회하면 실패해야 한다.
- `throwsAuthenticationFailed_whenPasswordDoesNotMatch`
  - 비밀번호가 일치하지 않으면 실패해야 한다.

#### 4차 Red: 비밀번호 수정

- `changesPassword_whenCurrentPasswordAndNewPasswordAreValid`
  - 기존 비밀번호가 일치하고 새 비밀번호가 유효하면 비밀번호가 변경되어야 한다.
- `throwsNotFound_whenChangingPasswordForUnknownLoginId`
  - 가입되지 않은 로그인 ID로 비밀번호를 수정하면 실패해야 한다.
- `throwsAuthenticationFailed_whenCurrentPasswordDoesNotMatch`
  - 기존 비밀번호가 일치하지 않으면 실패해야 한다.
- `throwsSamePassword_whenNewPasswordIsSameAsCurrentPassword`
  - 새 비밀번호가 현재 비밀번호와 같으면 실패해야 한다.
- `throwsInvalidPassword_whenNewPasswordContainsBirthDate`
  - 새 비밀번호가 생년월일을 포함하면 실패해야 한다.
- `throwsInvalidPassword_whenNewPasswordContainsUnsupportedSpecialCharacter`
  - 새 비밀번호가 `!@#$%^&*()` 외 특수문자를 포함하면 실패해야 한다.

### Green Phase 통과 목록

Green Phase에서는 Red Phase에서 작성한 실패 테스트를 통과시키는 최소 구현만 작성한다. 아래 목록은 최종적으로 통과해야 할 UserService 단위 테스트 목록이다.

#### 1. 회원가입

- `registersUser_whenUserInfoIsValid`
  - 유효한 로그인 ID, 비밀번호, 이름, 생년월일, 이메일을 전달하면 사용자를 등록한다.
- `throwsAlreadyExists_whenLoginIdAlreadyExists`
  - 이미 가입된 로그인 ID로 가입하면 예외가 발생한다.
- `throwsInvalidLoginId_whenLoginIdContainsInvalidCharacters`
  - 로그인 ID가 영문과 숫자 외 문자를 포함하면 예외가 발생한다.
- `throwsInvalidPassword_whenPasswordLengthIsTooShort`
  - 비밀번호가 8자 미만이면 예외가 발생한다.
- `throwsInvalidPassword_whenPasswordLengthIsTooLong`
  - 비밀번호가 16자를 초과하면 예외가 발생한다.
- `throwsInvalidPassword_whenPasswordContainsUnsupportedCharacter`
  - 비밀번호가 허용되지 않은 문자를 포함하면 예외가 발생한다.
- `throwsInvalidPassword_whenPasswordContainsUnsupportedSpecialCharacter`
  - 비밀번호가 `!@#$%^&*()` 외 특수문자를 포함하면 예외가 발생한다.
- `throwsInvalidPassword_whenPasswordContainsBirthDate`
  - 비밀번호가 생년월일을 포함하면 예외가 발생한다.
- `throwsUserNameRequired_whenNameIsNull`
  - 이름이 null이면 예외가 발생한다.
- `throwsUserNameRequired_whenNameIsBlank`
  - 이름이 blank이면 예외가 발생한다.
- `throwsBirthDateRequired_whenBirthDateIsNull`
  - 생년월일이 null이면 예외가 발생한다.
- `throwsEmailRequired_whenEmailIsNull`
  - 이메일이 null이면 예외가 발생한다.
- `throwsEmailRequired_whenEmailIsBlank`
  - 이메일이 blank이면 예외가 발생한다.
- `throwsInvalidEmail_whenEmailFormatIsInvalid`
  - 이메일이 일반적인 `local@domain.tld` 형식이 아니면 예외가 발생한다.

#### 2. 내 정보 조회

- `returnsMyInfo_whenLoginHeadersAreValid`
  - 로그인 ID와 비밀번호가 가입된 사용자 정보와 일치하면 내 정보를 반환한다.
- `returnsMaskedName_whenMyInfoIsReturned`
  - 내 정보 조회 결과의 이름은 마지막 글자가 `*`로 마스킹된다.
- `throwsNotFound_whenLoginIdDoesNotExist`
  - 가입되지 않은 로그인 ID로 조회하면 예외가 발생한다.
- `throwsAuthenticationFailed_whenPasswordDoesNotMatch`
  - 로그인 ID는 존재하지만 비밀번호가 일치하지 않으면 예외가 발생한다.

#### 3. 비밀번호 수정

- `changesPassword_whenCurrentPasswordAndNewPasswordAreValid`
  - 기존 비밀번호가 일치하고 새 비밀번호가 규칙을 만족하면 비밀번호를 변경한다.
- `throwsNotFound_whenChangingPasswordForUnknownLoginId`
  - 가입되지 않은 로그인 ID로 비밀번호를 수정하면 예외가 발생한다.
- `throwsAuthenticationFailed_whenCurrentPasswordDoesNotMatch`
  - 기존 비밀번호가 일치하지 않으면 예외가 발생한다.
- `throwsSamePassword_whenNewPasswordIsSameAsCurrentPassword`
  - 새 비밀번호가 현재 비밀번호와 같으면 예외가 발생한다.
- `throwsInvalidPassword_whenNewPasswordLengthIsTooShort`
  - 새 비밀번호가 8자 미만이면 예외가 발생한다.
- `throwsInvalidPassword_whenNewPasswordLengthIsTooLong`
  - 새 비밀번호가 16자를 초과하면 예외가 발생한다.
- `throwsInvalidPassword_whenNewPasswordContainsUnsupportedCharacter`
  - 새 비밀번호가 허용되지 않은 문자를 포함하면 예외가 발생한다.
- `throwsInvalidPassword_whenNewPasswordContainsUnsupportedSpecialCharacter`
  - 새 비밀번호가 `!@#$%^&*()` 외 특수문자를 포함하면 예외가 발생한다.
- `throwsInvalidPassword_whenNewPasswordContainsBirthDate`
  - 새 비밀번호가 생년월일을 포함하면 예외가 발생한다.

## 비기능적 요구사항

### 1. 테스트 작성 방식

- `.note/테스트코드.md`의 테스트 규칙을 따른다.
- Red > Green > Refactor 흐름으로 진행한다.
- 모든 테스트는 Arrange - Act - Assert 구조로 작성한다.
- 테스트 이름과 `@DisplayName`은 요구사항을 설명해야 한다.
- 테스트는 서로 독립적으로 실행되어야 하며 실행 순서에 의존하지 않는다.
- 실제 동작하지 않는 코드나 의미 없는 Mock 데이터로 테스트를 통과시키지 않는다.
- `println`을 테스트 코드나 구현 코드에 남기지 않는다.
- 기존 구현을 기준으로 테스트를 맞추지 않고, 이 문서의 요구사항을 기준으로 실패 테스트를 먼저 작성한다.

### 2. 설계 및 구현 방식

- 실제 동작하는 해결책만 구현한다.
- null-safety를 고려한다.
- 테스트 가능한 구조로 설계한다.
- 기존 구현과 비교하는 단계 전까지는 기존 구현 코드를 참조하지 않는다.
- 구현 비교 후에는 기존 코드 패턴과 일관성을 맞춘다.
- Java에서는 null 반환보다 `Optional` 사용을 우선 고려한다.

### 3. 데이터 정합성

- 중복 로그인 ID는 저장소 레벨에서도 방어되어야 한다.
- 인증이 필요한 요청은 로그인 ID와 비밀번호가 모두 일치해야 처리한다.

## 통합 테스트 목록

통합 테스트는 실제 Spring Context, JPA Repository, DB 제약, 트랜잭션 경로가 필요한 항목만 검증한다.

- `registersUser_whenUserInfoIsValid`
  - 회원가입 요청이 실제 DB에 저장되는지 검증한다.
- `throwsAlreadyExists_whenLoginIdAlreadyExists`
  - 이미 가입된 로그인 ID로 회원가입하면 서비스 레벨에서 실패하는지 검증한다.
- `throwsDataIntegrityViolation_whenDuplicateLoginIdIsSavedDirectly`
  - 서비스 레벨을 우회해도 DB unique 제약이 중복 로그인 ID를 막는지 검증한다.
- `returnsMyInfo_whenLoginHeadersAreValid`
  - 저장된 비밀번호를 기준으로 인증 후 내 정보를 조회할 수 있는지 검증한다.
- `throwsAuthenticationFailed_whenPasswordDoesNotMatch`
  - 저장된 비밀번호 기준으로 인증 실패가 동작하는지 검증한다.
- `changesPassword_whenCurrentPasswordAndNewPasswordAreValid`
  - 비밀번호 수정 후 새 비밀번호로 인증되는지 검증한다.

## 결정된 사항

- 중복 로그인 ID 가입은 `USER_ALREADY_EXISTS` 에러 타입으로 처리한다.
- 중복 로그인 ID는 애플리케이션에서 먼저 검사하고, DB unique 제약으로도 최종 방어한다.
- 이름이 null 또는 blank이면 `USER_NAME_REQUIRED` 에러 타입으로 처리한다.
- 생년월일이 null이면 `BIRTH_DATE_REQUIRED` 에러 타입으로 처리한다.
- 이메일이 null 또는 blank이면 `EMAIL_REQUIRED` 에러 타입으로 처리한다.
- 이메일 형식이 올바르지 않으면 `EMAIL_INVALID_FORMAT` 에러 타입으로 처리한다.
- 비밀번호 특수문자는 `!@#$%^&*()`만 허용한다.
- 비밀번호 수정 요청은 기존 비밀번호를 `X-Loopers-LoginPw` 헤더로 받고, 본문에는 새 비밀번호만 받는다.

## 공통 API 전제

- 회원가입 이후 유저 정보가 필요한 모든 요청은 `X-Loopers-LoginId`, `X-Loopers-LoginPw` 헤더로 사용자를 식별한다.
- 헤더의 로그인 ID와 비밀번호가 실제 가입된 사용자 정보와 일치해야 요청을 처리할 수 있다.

## TFD 진행 방식

1. 이 문서의 기능 정의만 보고 실패하는 테스트를 먼저 작성한다.
2. 테스트 작성 전에는 기존 구현 코드를 참조하지 않는다.
3. 실패 결과를 확인한 뒤 현재 구현과 비교한다.
4. 비교 결과를 구현 누락, 테스트 과잉 해석, 요구사항 불명확으로 분류한다.
5. 요구사항이 불명확하면 구현하지 않고 결정 사항으로 남긴다.
