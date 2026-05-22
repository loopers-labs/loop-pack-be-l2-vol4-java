package com.loopers.security;

import lombok.Value;

@Value
public class UserPrincipal {
    int id;
    String loginId;
    String role;
}
