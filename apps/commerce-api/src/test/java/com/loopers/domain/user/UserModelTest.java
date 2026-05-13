package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.stream.Stream;

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
            String loginId = "testid";
            String password = "testPw1234";
            String name = "테스터";
            LocalDate birthday = LocalDate.of(1992, 6, 24);
            String email = "test@example.com";

            // Act (실행) - 실제로 테스트하려는 동작 수행
            UserModel userModel = new UserModel(loginId, password, name, birthday, email);

            // Assert (검증) - 결과가 기대한 대로인지 확인
            assertAll(
                    //assertj라는 테스트 검증 라이브러리의 검증 메소드 검증하고 싶은 대상을 메소드 인자로 받음
                    () -> assertThat(userModel.getLoginId()).isEqualTo(loginId),
                    () -> assertThat(userModel.getName()).isEqualTo(name),
                    () -> assertThat(userModel.getBirthday()).isEqualTo(birthday),
                    () -> assertThat(userModel.getEmail()).isEqualTo(email),
                    // 비밀번호는 암호화 하여 저장할것이기 때문에 존재 유무와 입력값과 검증로직을 타지않는경우 안맞는거 체크
                    () -> assertThat(userModel.getPassword()).isNotEqualTo(password)
            );
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

            // 해피케이스
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

        @Nested
        @DisplayName("생년월일 검증")
        class BirthdayValidation {

            @DisplayName("생년월일이 null이면 BAD_REQUEST 예외가 발생한다")
            @Test
            void given_nullBirthday_when_createUserModel_then_throwsBadRequestException() {
                // Arrange

                // Act
                CoreException result = assertThrows(
                        CoreException.class,
                        () -> aUser().withBirthday(null).build()
                );

                // Assert
                assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }

            @DisplayName("생년월일이 미래 날짜이면 BAD_REQUEST 예외가 발생한다")
            @ParameterizedTest
            @MethodSource("futureBirthdays") // ValueSource 와 다르게 동적 값이 필요할때
            void given_futureBirthday_when_createUserModel_then_throwsBadRequestException(LocalDate futureday) {
                // Arrange

                // Act
                CoreException result = assertThrows(
                        CoreException.class,
                        () -> aUser().withBirthday(futureday).build()
                );

                // Assert
                assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }

            static Stream<LocalDate> futureBirthdays() {
                LocalDate today = LocalDate.now();
                return Stream.of(
                        today.plusDays(1),      // 내일 (경계)
                        today.plusMonths(1),    // 한 달 후
                        today.plusYears(1),     // 1년 후
                        today.plusYears(10)     // 10년 후
                );
            }

            // 해피케이스
            @DisplayName("생년월일이 오늘이거나 과거 날짜이면 정상 생성된다")
            @ParameterizedTest
            @MethodSource("validBirthdays")
            void given_validBirthday_when_createUserModel_then_createsUserModel(LocalDate validBirthday) {
                UserModel userModel = aUser().withBirthday(validBirthday).build();
                assertThat(userModel.getBirthday()).isEqualTo(validBirthday);
            }

            static Stream<LocalDate> validBirthdays() {
                LocalDate today = LocalDate.now();
                return Stream.of(
                        today,                          // 오늘 (경계 — 허용)
                        today.minusDays(1),             // 어제
                        today.minusMonths(1),           // 한 달 전
                        today.minusYears(1),            // 1년 전
                        today.minusYears(30),           // 30년 전
                        LocalDate.of(1900, 1, 1)        // 오래된 과거
                );
            }
        }
    }

}
