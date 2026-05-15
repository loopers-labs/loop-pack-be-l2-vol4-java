package com.loopers.domain.user;

import com.loopers.domain.user.vo.EncodedPassword;
import com.loopers.domain.user.vo.PlainPassword;

public interface PasswordHasher {
    EncodedPassword hash(PlainPassword raw);
    boolean matches(PlainPassword raw, EncodedPassword encoded);
}