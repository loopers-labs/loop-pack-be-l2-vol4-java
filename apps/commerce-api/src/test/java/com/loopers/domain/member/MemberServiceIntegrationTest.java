package com.loopers.domain.member;

import com.loopers.infrastructure.member.MemberJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
public class MemberServiceIntegrationTest {
    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberJpaRepository memberJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private PasswordEncryptor passwordEncryptor;

    public String loginId;
    public String loginPassword;
    public String name ;
    public LocalDate birthday;
    public String email;


    @BeforeEach
    public void setUp() {
        loginId = "loopers";
        loginPassword = "pAssWord1!";
        name = "루퍼스";
        birthday = LocalDate.parse("2000-01-01");
        email = "email@email.com";
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원 가입을 할 때,")
    @Nested
    class JoinMember {
        @DisplayName("회원 정보가 모두 주어지면, 정상적으로 회원가입이 된다.")
        @Test
        void saveAndReturnMemberModel_whenValidInfoProvided(){
            // arrange

            // act
            Member member = memberService.join(loginId, loginPassword, name, birthday, email);

            // assert
            assertAll(
                    () -> assertThat(member.getLoginId()).isEqualTo(loginId),
                    () -> assertThat(member.getName()).isEqualTo(name),
                    () -> assertThat(member.getBirthday()).isEqualTo(birthday),
                    () -> assertThat(member.getEmail()).isEqualTo(email)
            );
        }

        @DisplayName("회원가입 성공 시, 비밀번호는 암호화되어 저장된다.")
        @Test
        void saveEncryptedPassword_whenJoinSucceeds() {
            // arrange

            // act
            Member member = memberService.join(loginId, loginPassword, name, birthday, email);

            // assert
            assertAll(
                    () -> assertThat(member.getLoginPassword()).isNotEqualTo(loginPassword),
                    () -> assertThat(passwordEncryptor.matches(loginPassword, member.getLoginPassword())).isTrue()
            );
        }

        @DisplayName("이미 가입된 로그인 ID가 있으면, 회원가입이 실패한다.")
        @Test
        void throwConflictException_whenLoginIdAlreadyExists(){
            // arrange
            memberJpaRepository.save(new Member(loginId, loginPassword, name, birthday, email));

            // act
            CoreException result = assertThrows(CoreException.class, () -> memberService.join(loginId, loginPassword, "홍길동", LocalDate.of(2000, 5, 5), "new@email.com"));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("로그인 패스워드는 8~16자의 영문 대소문자, 숫자, 특수문자만 가능하다.")
        @ParameterizedTest
        @ValueSource(strings = {"       ", "1234", "안녕하세요반갑습니다", "abcdefghijklmnopqrstuvwxyz"})
        void throwsBadRequestException_whenLoginPasswordIdIsInvalid(String invalidLoginPassword) {
            // arrange

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                memberService.join(loginId, invalidLoginPassword, name, birthday, email);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("로그인 패스워드에 생년월일이 포함되어 있으면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"Pass!20000101", "Pass!0101"})
        void throwsBadRequestException_whenLoginPasswordContainsBirthday(String loginPasswordWithBirthday) {
            // arrange

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                memberService.join(loginId, loginPasswordWithBirthday, name, birthday, email);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("회원 정보 조회를 할 때,")
    @Nested
    class GetMember {
        @DisplayName("올바른 로그인 ID와 비밀번호가 주어지면, 유저를 반환한다.")
        @Test
        void getMember_whenValidInfoProvided(){
            // arrange
            memberService.join(loginId, loginPassword, name, birthday, email);

            // act
            Member member = memberService.getMember(loginId, loginPassword);

            // assert
            assertAll(
                    () -> assertThat(member.getLoginId()).isEqualTo(loginId),
                    () -> assertThat(member.getName()).isEqualTo(name),
                    () -> assertThat(member.getBirthday()).isEqualTo(birthday),
                    () -> assertThat(member.getEmail()).isEqualTo(email)
            );
        }

        @DisplayName("존재하지 않는 로그인 ID면, UNAUTHORIZED 에러를 반환한다.")
        @Test
        void throwUnauthorizedException_whenLoginIdDoesNotExist(){
            // act
            CoreException result = assertThrows(CoreException.class, () -> { memberService.getMember(loginId, loginPassword);});

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("비밀번호가 올바르지 않으면, UNAUTHORIZED 에러를 반환한다.")
        @Test
        void throwUnauthorizedException_whenLoginPasswordDoesNotMatch(){
            // arrange
            memberService.join(loginId, loginPassword, name, birthday, email);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                memberService.getMember(loginId, "myPassWord1234");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }
    }

    @DisplayName("비밀번호 수정을 할 때,")
    @Nested
    class UpdatePassword {
        @DisplayName("비밀번호 규칙을 따르고, 변경하려는 비밀번호가 현재 비밀번호와 다른 경우 정상적으로 변경이 된다.")
        @Test
        void updatePassword_whenValidInfoProvided(){
            // arrange
            Member member = memberService.join(loginId, loginPassword, name, birthday, email);

            String newPassword = "pAssWord2!";

            // act
            memberService.changePassword(member, loginPassword, newPassword);

            // assert
            Member updatedMember = memberService.getMember(loginId, newPassword);

            assertAll(
                    () -> assertThat(updatedMember.getLoginId()).isEqualTo(loginId),
                    () -> assertThat(updatedMember.getName()).isEqualTo(name),
                    () -> assertThat(updatedMember.getBirthday()).isEqualTo(birthday),
                    () -> assertThat(updatedMember.getEmail()).isEqualTo(email)
            );

        }

        @DisplayName("변경하려는 비밀번호가 현재 비밀번호가 같은 경우 BAD_REQUEST 에러를 반환한다.")
        @Test
        void throwBadRequestException_whenNewPasswordEqualsToCurrentPassword(){
            // arrange
            Member member = memberService.join(loginId, loginPassword, name, birthday, email);

            CoreException result = assertThrows(CoreException.class, () -> {
                memberService.changePassword(member, loginPassword, loginPassword);
            });

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("변경하려는 비밀번호가 비밀번호 규칙을 따르지 않는 경우 BAD_REQUEST 에러를 반환한다.")
        @ParameterizedTest
        @ValueSource(strings = {"       ", "1234", "안녕하세요반갑습니다", "abcdefghijklmnopqrstuvwxyz", "Pass!20000101", "Pass!0101"})
        void throwBadRequestException_whenNewPasswordDoesNotMatch(String wrongPassword){
            Member member = memberService.join(loginId, loginPassword, name, birthday, email);

            CoreException result = assertThrows(CoreException.class, () -> {
                memberService.changePassword(member, loginPassword, wrongPassword);
            });

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

}
