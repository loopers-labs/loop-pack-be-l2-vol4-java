package com.loopers.domain.user;

import com.loopers.domain.user.enums.UserRole;
import com.loopers.domain.user.vo.BirthDay;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.Name;
import com.loopers.domain.user.vo.Password;
import com.loopers.domain.user.vo.UserId;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class UserRepositoryTest {

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private static final String DEFAULT_USERID   = "user1";
    private static final String DEFAULT_PASSWORD = "Dlaxodid1!";
    private static final String DEFAULT_NAME     = "홍길동";
    private static final String DEFAULT_BIRTHDAY = "1990-01-01";
    private static final String DEFAULT_EMAIL    = "test@test.com";

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private UserModel buildDefaultUser() {
        return new UserModel(
                new UserId(DEFAULT_USERID),
                new Password(passwordEncoder.encode(DEFAULT_PASSWORD)),
                new Name(DEFAULT_NAME),
                new BirthDay(DEFAULT_BIRTHDAY),
                new Email(DEFAULT_EMAIL),
                UserRole.USER
        );
    }

    @DisplayName("중복된 아이디로 저장하면, DataIntegrityViolationException 이 발생한다.")
    @Test
    void throwsException_whenDuplicateUserIdIsInserted() {
        userRepository.save(buildDefaultUser());

        assertThrows(DataIntegrityViolationException.class, () ->
                userRepository.save(buildDefaultUser())
        );
    }
}
