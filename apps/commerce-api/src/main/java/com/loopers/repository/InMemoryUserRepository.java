package com.loopers.repository;

import com.loopers.model.User;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Repository
public class InMemoryUserRepository {

    private final List<User> users = new ArrayList<>();
    private final AtomicInteger idCounter = new AtomicInteger(2);

    public InMemoryUserRepository() {
        User admin = new User();
        admin.setId(1);
        admin.setLoginId("admin");
        admin.setName("관리자");
        admin.setEmail("admin@textfix.kr");
        admin.setPasswordHash("$2a$10$abcdefghijklmnopqrstuuVnqsN9RZlQNHWn2kPQg4L5kT4YMlxnW");
        admin.setRole("civilian");
        admin.setCreatedAt(Instant.now().toString());
        users.add(admin);
    }

    public User findById(int id) {
        return users.stream().filter(u -> u.getId() == id).findFirst().orElse(null);
    }

    public User findByLoginId(String loginId) {
        return users.stream().filter(u -> loginId.equals(u.getLoginId())).findFirst().orElse(null);
    }

    public User findByEmail(String email) {
        return users.stream().filter(u -> email.equals(u.getEmail())).findFirst().orElse(null);
    }

    public User save(User user) {
        if (user.getId() == 0) {
            user.setId(idCounter.getAndIncrement());
            user.setCreatedAt(Instant.now().toString());
            users.add(user);
        } else {
            for (int i = 0; i < users.size(); i++) {
                if (users.get(i).getId() == user.getId()) { users.set(i, user); break; }
            }
        }
        return user;
    }

    public boolean deleteById(int id) {
        return users.removeIf(u -> u.getId() == id);
    }

    public void addPasswordHistory(int id, String oldHash) {
        User user = findById(id);
        if (user != null) user.getPasswordHistory().add(oldHash);
    }

    public void clear() {
        users.clear();
        idCounter.set(1);
    }
}
