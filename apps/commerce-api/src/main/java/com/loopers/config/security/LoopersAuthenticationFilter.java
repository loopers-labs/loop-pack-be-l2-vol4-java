package com.loopers.config.security;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class LoopersAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    public static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {

        String loginId = request.getHeader(HEADER_LOGIN_ID);
        String password = request.getHeader(HEADER_LOGIN_PW);

        if (loginId != null && password != null) {
            authenticate(loginId, password);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String loginId, String rawPassword) {
        Optional<UserModel> userOpt = userRepository.findByLoginId(loginId);
        if (userOpt.isEmpty() || !passwordEncoder.matches(rawPassword, userOpt.get().getPassword())) {
            return;
        }
        UserModel user = userOpt.get();
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(user.getId(), null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}