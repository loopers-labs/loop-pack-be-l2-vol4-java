package com.loopers.infrastructure.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import com.loopers.domain.user.PasswordEncrypter;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest
class UserRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncrypter passwordEncrypter;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private UserModel createUser(String rawLoginId, String rawEmail) {
        UserModel newUser = UserModel.builder()
            .rawLoginId(rawLoginId)
            .rawPassword("Kyle!2030")
            .rawName("김카일")
            .rawBirthDate(LocalDate.of(1995, 3, 21))
            .rawEmail(rawEmail)
            .passwordEncrypter(passwordEncrypter)
            .build();

        return userRepository.save(newUser);
    }

    @DisplayName("회원을 저장할 때,")
    @Nested
    class Save {

        @DisplayName("저장한 회원을 id로 다시 조회하면 같은 회원 정보를 그대로 가져온다.")
        @Test
        void canBeFoundById_withSameUserFields() {
            // arrange & act
            UserModel savedUser = createUser("kyleKim", "kyle@example.com");
            Optional<UserModel> foundUser = userRepository.findById(savedUser.getId());

            // assert
            assertAll(
                () -> assertThat(foundUser).isPresent(),
                () -> assertThat(foundUser.get().getId()).isEqualTo(savedUser.getId()),
                () -> assertThat(foundUser.get().getLoginId()).isEqualTo(savedUser.getLoginId()),
                () -> assertThat(foundUser.get().getEncryptedPassword()).isEqualTo(savedUser.getEncryptedPassword()),
                () -> assertThat(foundUser.get().getName()).isEqualTo(savedUser.getName()),
                () -> assertThat(foundUser.get().getBirthDate()).isEqualTo(savedUser.getBirthDate()),
                () -> assertThat(foundUser.get().getEmail()).isEqualTo(savedUser.getEmail())
            );
        }

        @DisplayName("이미 사용 중인 로그인 ID로 가입을 시도하면 예외가 발생한다.")
        @Test
        void throwsException_whenLoginIdAlreadyExists() {
            // arrange
            createUser("kyleKim", "kyle@example.com");

            // act & assert
            assertThatThrownBy(() -> createUser("kyleKim", "other@example.com"))
                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @DisplayName("이미 사용 중인 이메일로 가입을 시도하면 예외가 발생한다.")
        @Test
        void throwsException_whenEmailAlreadyExists() {
            // arrange
            createUser("kyleKim", "kyle@example.com");

            // act & assert
            assertThatThrownBy(() -> createUser("otherKim", "kyle@example.com"))
                .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @DisplayName("로그인 ID가 존재하는지 조회할 때,")
    @Nested
    class ExistsByLoginId {

        @DisplayName("저장된 로그인 ID면 true, 저장되지 않은 로그인 ID면 false를 반환한다.")
        @Test
        void returnsTrueForSavedLoginId_andFalseOtherwise() {
            // arrange
            createUser("kyleKim", "kyle@example.com");

            // act & assert
            assertAll(
                () -> assertThat(userRepository.existsByLoginId("kyleKim")).isTrue(),
                () -> assertThat(userRepository.existsByLoginId("unknown99")).isFalse()
            );
        }
    }

    @DisplayName("이메일이 존재하는지 조회할 때,")
    @Nested
    class ExistsByEmail {

        @DisplayName("저장된 이메일이면 true, 저장되지 않은 이메일이면 false를 반환한다.")
        @Test
        void returnsTrueForSavedEmail_andFalseOtherwise() {
            // arrange
            createUser("kyleKim", "kyle@example.com");

            // act & assert
            assertAll(
                () -> assertThat(userRepository.existsByEmail("kyle@example.com")).isTrue(),
                () -> assertThat(userRepository.existsByEmail("unknown@example.com")).isFalse()
            );
        }
    }

    @DisplayName("로그인 ID로 회원을 조회할 때,")
    @Nested
    class FindByLoginId {

        @DisplayName("저장된 로그인 ID면 해당 회원을 담은 Optional을 반환한다.")
        @Test
        void returnsUser_whenLoginIdIsSaved() {
            // arrange
            UserModel savedUser = createUser("kyleKim", "kyle@example.com");

            // act
            Optional<UserModel> foundUser = userRepository.findByLoginId("kyleKim");

            // assert
            assertAll(
                () -> assertThat(foundUser).isPresent(),
                () -> assertThat(foundUser.get().getId()).isEqualTo(savedUser.getId()),
                () -> assertThat(foundUser.get().getLoginId()).isEqualTo(savedUser.getLoginId())
            );
        }

        @DisplayName("저장되지 않은 로그인 ID면 비어 있는 Optional을 반환한다.")
        @Test
        void returnsEmpty_whenLoginIdIsNotSaved() {
            // arrange
            createUser("kyleKim", "kyle@example.com");

            // act
            Optional<UserModel> foundUser = userRepository.findByLoginId("unknown99");

            // assert
            assertThat(foundUser).isEmpty();
        }
    }
}
