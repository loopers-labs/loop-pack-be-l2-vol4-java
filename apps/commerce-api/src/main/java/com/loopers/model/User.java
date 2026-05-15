package com.loopers.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class User {
    private int id;
    private String loginId;
    private String name;
    private String birthdate;
    private String email;
    private String passwordHash;
    private String duressPasswordHash;
    private List<String> passwordHistory = new ArrayList<>();
    private String role;
    private String createdAt;
}
