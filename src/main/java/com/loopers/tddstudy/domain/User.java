package com.loopers.tddstudy.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private  Long id ;

    private String loginId;
    private String loginPw;
    private String name;
    private String birthDate;
    private String email;

    protected  User(){}

    public  User (String loginId, String loginPw, String name, String birthDate, String email){
        validateLoginId(loginId);
        validatePassword(loginPw, birthDate);
        validateEmail(email);

        this.loginId = loginId;
        this.loginPw = loginPw;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;

    }

    //로그인ID 체크
    private void validateLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            throw new IllegalArgumentException("로그인 ID는 필수입니다.");
        }
        if (!loginId.matches("^[a-zA-Z0-9]+$")){
            throw new IllegalArgumentException("로그인 ID는 영문과 숫자만 가능합니다.");
        }

    }

    //로그인 패스워드 체크
    private void validatePassword(String loginPw, String birthDate) {
        if (loginPw == null || loginPw.length() < 8 || loginPw.length() > 16) {
            throw new IllegalArgumentException("비밀번호는 8~16자여야 합니다.");
        }
        if (!loginPw.matches("^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]*$")) {
            throw new IllegalArgumentException("비밀번호는 영문 대소문자, 숫자, 특수문자만 가능합니다.");
        }
        if (birthDate != null && loginPw.contains(birthDate)) {
            throw new IllegalArgumentException("비밀번호에 생년월일을 포함할 수 없습니다.");
        }

    }

    //이메일 형식 체크
    private void validateEmail(String email) {
        if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("이메일 형식이 올바르지 않습니다.");
        }
    }

    //사용자 비밀번호 수정
    public void changePassword(String newPassword , String currentPassword) {
        if(!matchesPassword(currentPassword)){
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }
        if (newPassword.equals(currentPassword)){
            throw new IllegalArgumentException("현재 비밀번호와 동일한 비밀번호로 변경할 수 없습니다.");
        }
        validatePassword(newPassword,this.birthDate);
        this.loginPw = newPassword;
    }

    public boolean matchesPassword(String  password ){
        return  this.loginPw.equals(password);
    }


    public Long getId() { return id; }
    public String getLoginId() { return loginId; }
    public String getLoginPw() { return loginPw; }
    public String getName() { return name; }
    public String getBirthDate() { return birthDate; }
    public String getEmail() { return email; }

}
