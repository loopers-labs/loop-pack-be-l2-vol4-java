package com.loopers.security;

import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class HeaderAuthFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String loginId = request.getHeader("X-Loopers-LoginId");
        String password = request.getHeader("X-Loopers-LoginPw");

        if (loginId != null && password != null) {
            Optional<User> userOpt = userRepository.findByLoginId(loginId);
            if (userOpt.isPresent() && userOpt.get().matchesPassword(password, passwordEncoder)) {
                User user = userOpt.get();
                UserPrincipal principal = new UserPrincipal(user.getId(), user.getLoginId());
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        filterChain.doFilter(request, response);
    }
}
