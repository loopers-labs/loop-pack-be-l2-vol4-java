package com.loopers.domain.member;

import com.loopers.infrastructure.member.MemberJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
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

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원 가입을 할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 정보로 가입하면, 회원이 저장된다.")
        @Test
        void saveMember_whenValidInfoProvided() {
            // act
            MemberModel member = memberService.registerMember("testuser", "Password1!", "test@example.com", "김테스트", "19940201");

            // assert
            assertAll(
                () -> assertThat(member).isNotNull(),
                () -> assertThat(member.getId()).isNotNull(),
                () -> assertThat(member.getUserId()).isEqualTo("testuser")
            );
        }

        @DisplayName("이미 가입된 로그인 ID 로 가입을 시도하면 가입이 불가능하다")
        @Test
        void throwsException_whenExistIdProvided() {
            // arrange
            memberJpaRepository.save(new MemberModel("eccsck", "Password1!", "eccsck@gmail.com", "김승찬", "19940201"));

            // act & assert
            assertThatThrownBy(() -> memberService.registerMember("eccsck", "Password1!", "eccsck@gmail.com", "김승찬", "19940201"))
                    .isInstanceOf(CoreException.class)
                    .hasMessage("이미 존재하는 유저 ID입니다.");
        }
    }

    @DisplayName("회원 조회를 할때,")
    @Nested
    class Get {

        @DisplayName("유효한 회원 ID를 주면, 회원 정보를 반환한다.")
        @Test
        void returnsMemberInfo_whenValidIdProvided() {
            // arrange
            MemberModel saved = memberJpaRepository.save(
                new MemberModel("testuser", "Password1!", "test@example.com", "김테스트", "19940201")
            );

            // act
            MemberModel member = memberService.getMember(saved.getId());

            // assert
            assertAll(
                () -> assertThat(member.getUserId()).isEqualTo("testuser"),
                () -> assertThat(member.getMaskedUsername()).isEqualTo("김테스*"),
                () -> assertThat(member.getEmail()).isEqualTo("test@example.com"),
                () -> assertThat(member.getBirthDate()).isEqualTo("19940201")
            );
        }

        @DisplayName("존재하지 않는 회원 ID 를 주면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenNonExistentIdProvided() {
            // arrange
            Long invalidId = -1L;

            // act
            CoreException exception = assertThrows(CoreException.class,
                () -> memberService.getMember(invalidId));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("회원 정보를 수정할때,")
    @Nested
    class Update {

        @DisplayName("유효한 새 비밀번호로 변경하면 성공한다.")
        @Test
        void updatesPassword_whenValidPasswordProvided() {
            // arrange
            MemberModel saved = memberJpaRepository.save(
                new MemberModel("testuser", "Password1!", "test@example.com", "김테스트", "19940201")
            );

            // act & assert (예외 없이 정상 종료)
            memberService.updatePassword(saved.getId(), "Password1!", "NewPass2@");
        }

        @DisplayName("기존 비밀번호와 같은 비밀번호로 변경을 시도하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsException_whenSamePasswordProvided() {
            // arrange
            MemberModel saved = memberJpaRepository.save(
                new MemberModel("testuser", "Password1!", "test@example.com", "김테스트", "19940201")
            );

            // act
            CoreException exception = assertThrows(CoreException.class,
                () -> memberService.updatePassword(saved.getId(), "Password1!", "Password1!"));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("형식에 맞지 않는 비밀번호로 변경을 시도하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsException_whenInvalidPasswordProvided() {
            // arrange
            MemberModel saved = memberJpaRepository.save(
                new MemberModel("testuser", "Password1!", "test@example.com", "김테스트", "19940201")
            );

            // act
            CoreException exception = assertThrows(CoreException.class,
                () -> memberService.updatePassword(saved.getId(), "Password1!", "short"));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("존재하지 않는 회원 ID 로 비밀번호 변경을 시도하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenNonExistentIdProvided() {
            // arrange
            Long invalidId = -1L;

            // act
            CoreException exception = assertThrows(CoreException.class,
                () -> memberService.updatePassword(invalidId, "Password1!", "NewPass2@"));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
