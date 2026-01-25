package com.lyshra.open.app.designer.security;

import com.lyshra.open.app.designer.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

/**
 * Custom user details service for Spring Security.
 */
@Service
public class CustomUserDetailsService implements ReactiveUserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Mono<UserDetails> findByUsername(String username) {
        return userRepository.findByUsername(username)
                .flatMap(optUser -> optUser
                        .map(user -> {
                            var authorities = user.getRoles().stream()
                                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                                    .collect(Collectors.toList());

                            return Mono.just((UserDetails) User.builder()
                                    .username(user.getUsername())
                                    .password(user.getPasswordHash())
                                    .authorities(authorities)
                                    .disabled(!user.isEnabled())
                                    .accountLocked(user.isAccountLocked())
                                    .build());
                        })
                        .orElse(Mono.empty()));
    }
}
