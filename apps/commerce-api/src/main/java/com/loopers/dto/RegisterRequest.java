package com.loopers.dto;
import lombok.Data;
@Data
public class RegisterRequest {
    private String loginId;
    private String password;
    private String name;
    private String birthdate;
    private String email;
    private String duressPassword;
    private String referral;
}
