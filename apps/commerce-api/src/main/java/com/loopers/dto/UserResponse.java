package com.loopers.dto;

import com.loopers.model.User;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserResponse {
    private String loginId;
    private String name;
    private String birthdate;
    private String email;
    private String role;

    public static UserResponse from(User user) {
        return new UserResponse(user.getLoginId(), user.getName(), user.getBirthdate(), user.getEmail(), user.getRole());
    }
}
