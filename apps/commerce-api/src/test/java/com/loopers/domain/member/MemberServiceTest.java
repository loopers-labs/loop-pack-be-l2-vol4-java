package com.loopers.domain.member;

import com.loopers.infrastructure.member.MemberJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
public class MemberServiceTest {
    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberJpaRepository memberJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

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
                    () -> assertThat(member.getLoginPassword()).isEqualTo(loginPassword),
                    () -> assertThat(member.getName()).isEqualTo(name),
                    () -> assertThat(member.getBirthday()).isEqualTo(birthday),
                    () -> assertThat(member.getEmail()).isEqualTo(email)
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
    }

}
