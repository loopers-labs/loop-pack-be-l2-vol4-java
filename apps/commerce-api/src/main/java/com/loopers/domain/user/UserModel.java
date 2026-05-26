package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.user.vo.BirthDate;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.EncodedPassword;
import com.loopers.domain.user.vo.LoginId;
import com.loopers.domain.user.vo.PlainPassword;
import com.loopers.domain.user.vo.UserName;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "users")
public class UserModel extends BaseEntity {

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "login_id", unique = true))
    private LoginId loginId;

    @Column(name = "password")
    private String password;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "name"))
    private UserName name;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "birth_date"))
    private BirthDate birthDate;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "email"))
    private Email email;

    protected UserModel() {}

    public UserModel(String loginId, String password, String name, LocalDate birthDate, String email) {
        this.loginId = new LoginId(loginId);
        this.birthDate = new BirthDate(birthDate);
        this.password = new PlainPassword(password, birthDate).value();
        this.name = new UserName(name);
        this.email = new Email(email);
    }

    public void encodePassword(PasswordHasher hasher) {
        this.password = hasher.hash(this.password);
    }

    void updatePassword(String encodedNewPassword) {
        this.password = new EncodedPassword(encodedNewPassword).value();
    }

    public String getPassword() {
        return password;
    }

    public String getLoginId() {
        return loginId.value();
    }

    public String getName() {
        return name.value();
    }

    public LocalDate getBirthDate() {
        return birthDate.value();
    }

    public String getEmail() {
        return email.value();
    }

    public String getMaskedName() {
        return name.masked();
    }
}
