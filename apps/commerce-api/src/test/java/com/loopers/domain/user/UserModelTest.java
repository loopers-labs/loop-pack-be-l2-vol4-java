package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static com.loopers.fixture.UserModelFixture.aUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class UserModelTest {
    //바텀업 (Bottom-Up, Inside-Out) 방식으로 구현을 시도

    @Nested
    @DisplayName("UserModel 생성")
    class CreateUserModel {
        @DisplayName("유효한 값으로 UserModel을 생성한다")
        @Test
        // 네이밍은 BDD(Behavior-Driven Development, 행동 주도 개발) 스타일 테스트 메서드 네이밍 컨벤션
        void given_validInput_when_createUserModel_then_createsUserModel(){
            // AAA 패턴 (Arrange-Act-Assert)

            // Arrange (준비) - 테스트에 필요한 데이터/객체 세팅
            String id = "testId";
            String password = "testPw1234";
            String name = "테스터";
            LocalDate birthday = LocalDate.of(1992, 6, 24);
            String email = "test@example.com";

            // Act (실행) - 실제로 테스트하려는 동작 수행
            UserModel userModel = new UserModel(id, password, name, birthday, email);

            // Assert (검증) - 결과가 기대한 대로인지 확인
            assertAll(
                    //assertj라는 테스트 검증 라이브러리의 검증 메소드 검증하고 싶은 대상을 메소드 인자로 받음
                    () -> assertThat(userModel.getId()).isEqualTo(id),
                    () -> assertThat(userModel.getName()).isEqualTo(name),
                    () -> assertThat(userModel.getBirthday()).isEqualTo(birthday),
                    () -> assertThat(userModel.getEmail()).isEqualTo(email),
                    // 비밀번호는 암호화 하여 저장할것이기 때문에 존재 유무와 입력값과 검증로직을 타지않는경우 안맞는거 체크
                    () -> assertThat(userModel.getPassword()).isNotEqualTo(password)
            );
        }

        @Nested
        @DisplayName("id 검증")
        class IdValidation {

            @DisplayName("아이디가 null이거나 공백이면 BAD_REQUEST 예외가 발생한다")
            @ParameterizedTest // 하나의 테스트 메서드를 여러 입력값으로 반복 실행할 수 있게
            @NullAndEmptySource // @NullSource + @EmptySource null과 빈 값을 한 번에 테스트 인자로 공급
            @ValueSource(strings = {" ", "   ", "\t", "\n"})
            void given_nullOrBlankId_when_createUserModel_then_throwsBadRequestException(String invalidId) {
                // Arrange
                // 보일러플레이트를 줄이기 위해 UserModelFixture 사용 - 검증 대상 필드만 invalid 값으로 주입

                // Act — 객체 생성 자체가 검증 대상이므로 람다 안에 넣는다
                CoreException result = assertThrows(
                        CoreException.class,
                        () -> aUser().withName(invalidId).build()
                );

                // Assert
                assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }

            @DisplayName("ID가 4자 미만이거나 20자를 초과하면 BAD_REQUEST 예외가 발생한다")
            @ParameterizedTest
            @ValueSource(strings = {
                    "abc",                       // 3자 (최소 미만)
                    "abcdefghij1234567890a"      // 21자 (최대 초과)
            })
            void given_idLengthOutOfRange_then_throwsBadRequest(String invalidId) {
                assertBadRequest(() -> aUser().withId(invalidId).build());
            }

            @DisplayName("ID에 허용되지 않은 문자가 포함되면 BAD_REQUEST 예외가 발생한다")
            @ParameterizedTest
            @ValueSource(strings = {
                    "User1234",            // 영문 대문자
                    "user1234!",           // 허용 외 특수문자(!)
                    "user 1234",           // 공백
                    "user@1234",           // @
                    "user한글",            // 한글
                    "user/1234",           // 경로 문자 (/)
                    "user\\1234",          // 경로 문자 (\)
                    "user..test"           // 연속된 점(경로 traversal 패턴)
            })
            void given_idWithInvalidCharacters_then_throwsBadRequest(String invalidId) {
                assertBadRequest(() -> aUser().withId(invalidId).build());
            }

            @DisplayName("ID가 숫자나 특수문자로 시작하면 BAD_REQUEST 예외가 발생한다")
            @ParameterizedTest
            @ValueSource(strings = {
                    "1user",               // 숫자로 시작
                    "_user",               // 특수문자로 시작
                    "-user"                // 특수문자로 시작
            })
            void given_idStartingWithNumberOrSpecial_then_throwsBadRequest(String invalidId) {
                assertBadRequest(() -> aUser().withId(invalidId).build());
            }

            @DisplayName("ID가 특수문자로 끝나면 BAD_REQUEST 예외가 발생한다")
            @ParameterizedTest
            @ValueSource(strings = {
                    "user_",
                    "user-"
            })
            void given_idEndingWithSpecial_then_throwsBadRequest(String invalidId) {
                assertBadRequest(() -> aUser().withId(invalidId).build());
            }

            @DisplayName("ID에 특수문자가 연속되면 BAD_REQUEST 예외가 발생한다")
            @ParameterizedTest
            @ValueSource(strings = {
                    "user__name",
                    "user--name",
                    "user-_name"
            })
            void given_idWithConsecutiveSpecials_then_throwsBadRequest(String invalidId) {
                assertBadRequest(() -> aUser().withId(invalidId).build());
            }
        }

        @Nested
        @DisplayName("비밀번호 검증")
        class PasswordValidation {

            @DisplayName("비밀번호가 null이거나 공백이면 BAD_REQUEST 예외가 발생한다")
            @ParameterizedTest
            @NullAndEmptySource
            @ValueSource(strings = {" ", "\t"})
            void given_nullOrBlankPassword_when_createUserModel_then_throwsBadRequestException(String invalidPassword) {
                // Act
                CoreException result = assertThrows(
                        CoreException.class,
                        () -> aUser().withPassword(invalidPassword).build()
                );

                // Assert
                assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }

            @DisplayName("비밀번호가 8자 미만이거나 16자를 초과하면 BAD_REQUEST 예외가 발생한다")
            @ParameterizedTest
            @ValueSource(strings = {
                    "Abc1!",                  // 5자 (미만)
                    "Abc123!",                // 7자 (미만, 경계 직전)
                    "Abc12345!Abcdefg",       // 17자 (초과, 경계 직후)
                    "Abc12345!Abcdefgh1234"   // 21자 (초과)
            })
            void given_passwordLengthOutOfRange_when_createUserModel_then_throwsBadRequestException(String invalidPassword) {
                // Act
                CoreException result = assertThrows(
                        CoreException.class,
                        () -> aUser().withPassword(invalidPassword).build()
                );

                // Assert
                assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }

            @DisplayName("비밀번호에 허용되지 않은 문자(한글 등)가 포함되면 BAD_REQUEST 예외가 발생한다")
            @ParameterizedTest
            @ValueSource(strings = {
                    "Password한글1!",          // 한글
                    "Pass word1!",             // 공백
                    "Password1!\t",            // 탭
                    "Pässword1!"               // 특수 유니코드
            })
            void given_passwordWithInvalidCharacters_when_createUserModel_then_throwsBadRequestException(String invalidPassword) {
                // Act
                CoreException result = assertThrows(
                        CoreException.class,
                        () -> aUser().withPassword(invalidPassword).build()
                );

                // Assert
                assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }

            @DisplayName("비밀번호에 생년월일(YYYYMMDD)이 포함되면 BAD_REQUEST 예외가 발생한다")
            @Test
            void given_passwordContainingBirthdayYYYYMMDD_when_createUserModel_then_throwsBadRequestException() {
                // Arrange
                LocalDate birthday = LocalDate.of(1992, 6, 24);
                String password = "Pass19920624!";

                // Act
                CoreException result = assertThrows(
                        CoreException.class,
                        () -> aUser().withBirthday(birthday).withPassword(password).build()
                );

                // Assert
                assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }

            @DisplayName("비밀번호에 생년월일(YYMMDD)이 포함되면 BAD_REQUEST 예외가 발생한다")
            @Test
            void given_passwordContainingBirthdayYYMMDD_when_createUserModel_then_throwsBadRequestException() {
                // Arrange
                LocalDate birthday = LocalDate.of(1992, 6, 24);
                String password = "Pass920624!";

                // Act
                CoreException result = assertThrows(
                        CoreException.class,
                        () -> aUser().withBirthday(birthday).withPassword(password).build()
                );

                // Assert
                assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }

            @DisplayName("비밀번호에 생년월일(MMDD)이 포함되면 BAD_REQUEST 예외가 발생한다")
            @Test
            void given_passwordContainingBirthdayMMDD_when_createUserModel_then_throwsBadRequestException() {
                // Arrange
                LocalDate birthday = LocalDate.of(1992, 6, 24);
                String password = "Password0624!";

                // Act
                CoreException result = assertThrows(
                        CoreException.class,
                        () -> aUser().withBirthday(birthday).withPassword(password).build()
                );

                // Assert
                assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }
        }

        @Nested
        @DisplayName("이름 검증")
        class NameValidation {

            @DisplayName("이름이 null이거나 공백이면 BAD_REQUEST 예외가 발생한다")
            @ParameterizedTest
            @NullAndEmptySource
            @ValueSource(strings = {" ", "   ", "\t", "\n"})
            void given_nullOrBlankName_when_createUserModel_then_throwsBadRequestException(String invalidName) {
                // Arrange

                // Act — 객체 생성 자체가 검증 대상이므로 람다 안에 넣는다
                CoreException result = assertThrows(
                        CoreException.class,
                        () -> aUser().withName(invalidName).build()
                );

                // Assert
                assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }

            @DisplayName("이름이 50자를 초과하면 BAD_REQUEST 예외가 발생한다")
            @Test
            void given_nameOverMaxLength_when_createUserModel_then_throwsBadRequestException() {
                // Arrange - 51자
                String tooLongName = "가".repeat(51);

                // Act
                CoreException result = assertThrows(
                        CoreException.class,
                        () -> aUser().withName(tooLongName).build()
                );

                // Assert
                assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }

            @DisplayName("이름에 허용되지 않은 문자(자모 분리, 숫자, 특수문자)가 포함되면 BAD_REQUEST 예외가 발생한다")
            @ParameterizedTest
            @ValueSource(strings = {
                    "정태형1",         // 숫자
                    "정태형!",         // 특수문자
                    "ㄱㄴㄷ",           // 자모 분리 (초성)
                    "ㅏㅓㅗ",           // 자모 분리 (모음)
                    "정태ㅎ",          // 완성형 + 자모 혼합
                    "Tae-hyung",       // 하이픈
                    "정.태.형"         // 점
            })
            void given_nameWithInvalidCharacters_when_createUserModel_then_throwsBadRequestException(String invalidName) {
                CoreException result = assertThrows(
                        CoreException.class,
                        () -> aUser().withName(invalidName).build()
                );
                assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }

            @DisplayName("이름이 허용 문자(한글/영문/공백)로만 구성되고 1~50자이면 정상 생성된다")
            @ParameterizedTest
            @ValueSource(strings = {
                    "정",                       // 1자 (최소 경계)
                    "정태형",                    // 한글
                    "John",                     // 영문
                    "John Doe",                 // 영문 + 공백
                    "정 태형",                   // 한글 + 공백
                    "John 정태형"                // 한글 + 영문 + 공백
            })
            void given_validName_when_createUserModel_then_createsUserModel(String validName) {
                UserModel userModel = aUser().withName(validName).build();
                assertThat(userModel.getName()).isEqualTo(validName);
            }

        }


    }

}
