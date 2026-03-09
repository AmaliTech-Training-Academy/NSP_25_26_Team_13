package com.logstream.service;

import com.logstream.config.JwtService;
import com.logstream.dto.AuthRequest;
import com.logstream.dto.AuthResponse;
import com.logstream.dto.RegisterRequest;
import com.logstream.model.Role;
import com.logstream.model.User;
import com.logstream.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getFullName())
                .role(Role.USER).build();
        userRepository.save(user);
        return generateTokenResponse(user);
    }

    public AuthResponse login(AuthRequest request) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                request.getEmail(), request.getPassword())
        );
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setLastLogin(Instant.now());
        userRepository.save(user);
        return generateTokenResponse(user);
    }

    private AuthResponse generateTokenResponse(User user) {
        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());
        return AuthResponse.builder().token(token).email(user.getEmail()).role(user.getRole().name()).build();
    }
}