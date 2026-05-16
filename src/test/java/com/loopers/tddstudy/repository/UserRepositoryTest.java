package com.loopers.tddstudy.repository;

import com.loopers.tddstudy.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
public class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("유저를 저장하고 loginId로 조회할 수 있다")
    void saveAndFindByLoginId() {
        User user = new User("lilpa123", "Pass1234!", "김릴파", "19901225", "lilpa@email.com");
        userRepository.save(user);

        Optional<User> found = userRepository.findByLoginId("lilpa123");

        assertThat(found).isPresent();
        assertThat(found.get().getLoginId()).isEqualTo("lilpa123");
    }

    @Test
    @DisplayName("존재하지 않는 loginId로 조회하면 빈 값을 반환한다")
    void findByLoginId_notFound() {
        Optional<User> found = userRepository.findByLoginId("없는아이디");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("이미 존재하는 loginId인지 확인할 수 있다")
    void existsByLoginId() {
        User user = new User("lilpa123", "Pass1234!", "김릴파", "19901225", "lilpa@email.com");
        userRepository.save(user);

        assertThat(userRepository.existsByLoginId("lilpa123")).isTrue();
        assertThat(userRepository.existsByLoginId("없는아이디")).isFalse();
    }

}
