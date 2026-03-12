package com.logstream.service;

import com.logstream.config.JwtService;
import com.logstream.dto.AuthRequest;
import com.logstream.dto.AuthResponse;
import com.logstream.dto.RegisterRequest;
import com.logstream.model.Role;
import com.logstream.model.User;
import com.logstream.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTests {

    private AuthService authService;

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private AuthenticationManager authenticationManager;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        authenticationManager = mock(AuthenticationManager.class);
        jwtService = mock(JwtService.class);

        authService = new AuthService(userRepository, passwordEncoder, authenticationManager, jwtService);
    }

    @Test
    void register_shouldSaveUserAndReturnToken() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setPassword("password");
        request.setFullName("Test User");

        when(userRepository.existsByEmailIgnoreCase("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password")).thenReturn("encodedPassword");
        when(jwtService.generateToken("test@example.com", "USER")).thenReturn("fakeToken");

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("test@example.com", response.getEmail());
        assertEquals("USER", response.getRole());
        assertEquals("fakeToken", response.getToken());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals("Test User", savedUser.getName());
        assertEquals("encodedPassword", savedUser.getPassword());
        assertEquals(Role.USER, savedUser.getRole());
    }

    @Test
    void register_shouldThrowExceptionIfEmailExists() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");

        when(userRepository.existsByEmailIgnoreCase("test@example.com")).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.register(request));
        assertEquals("Email already exists", ex.getMessage());

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_shouldAuthenticateAndReturnToken() {
        AuthRequest request = new AuthRequest();
        request.setEmail("test@example.com");
        request.setPassword("password");

        User user = User.builder()
                .email("test@example.com")
                .password("encodedPassword")
                .role(Role.USER)
                .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken("test@example.com", "USER")).thenReturn("fakeToken");

        AuthResponse response = authService.login(request);

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));

        assertNotNull(user.getLastLogin());
        assertEquals("test@example.com", response.getEmail());
        assertEquals("USER", response.getRole());
        assertEquals("fakeToken", response.getToken());
    }

    @Test
    void login_shouldThrowExceptionIfUserNotFound() {
        AuthRequest request = new AuthRequest();
        request.setEmail("notfound@example.com");
        request.setPassword("password");

        when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.login(request));
        assertEquals("User not found", ex.getMessage());
    }
}