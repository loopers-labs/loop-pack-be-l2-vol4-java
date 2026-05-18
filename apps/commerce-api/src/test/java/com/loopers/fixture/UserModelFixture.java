package com.loopers.fixture;

import com.loopers.domain.user.UserModel;

import java.time.LocalDate;

public class UserModelFixture {

    private String loginId = "testid";
    private String password = "testPw1234";
    private String name = "테스터";
    private LocalDate birthday = LocalDate.of(1992, 6, 24);
    private String email = "test@example.com";

    public static UserModelFixture aUser() {
        return new UserModelFixture();
    }

    public UserModelFixture withLoginId(String loginId) { this.loginId = loginId; return this; }
    public UserModelFixture withPassword(String password) { this.password = password; return this; }
    public UserModelFixture withName(String name) { this.name = name; return this; }
    public UserModelFixture withBirthday(LocalDate birthday) { this.birthday = birthday; return this; }
    public UserModelFixture withEmail(String email) { this.email = email; return this; }

    public UserModel build() {
        return new UserModel(loginId, password, name, birthday, email);
    }
}
