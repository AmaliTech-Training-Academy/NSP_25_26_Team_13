package com.logstream.service;

import com.logstream.config.JwtService;
import com.logstream.dto.AuthRequest;
import com.logstream.dto.AuthResponse;
import com.logstream.dto.RegisterRequest;
import com.logstream.dto.UserResponse;
import com.logstream.model.Role;
import com.logstream.model.User;
import com.logstream.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
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

    @Test
    void getAllUsers_shouldReturnAllUsers() {
        User user1 = User.builder().id(1L).email("user1@example.com").name("User One").role(Role.USER).active(true).build();
        User user2 = User.builder().id(2L).email("admin@example.com").name("Admin User").role(Role.ADMIN).active(true).build();

        when(userRepository.findAll()).thenReturn(Arrays.asList(user1, user2));

        List<UserResponse> users = authService.getAllUsers();

        assertNotNull(users);
        assertEquals(2, users.size());
        assertEquals("user1@example.com", users.get(0).getEmail());
        assertEquals("admin@example.com", users.get(1).getEmail());
        assertEquals("USER", users.get(0).getRole());
        assertEquals("ADMIN", users.get(1).getRole());
    }

    @Test
    void getUserById_shouldReturnUserWhenExists() {
        User user = User.builder()
                .id(1L)
                .email("test@example.com")
                .name("Test User")
                .role(Role.USER)
                .active(true)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse response = authService.getUserById(1L);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("test@example.com", response.getEmail());
        assertEquals("Test User", response.getName());
        assertEquals("USER", response.getRole());
    }

    @Test
    void getUserById_shouldThrowExceptionWhenNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.getUserById(999L));
        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void createUser_shouldSaveUserWithGivenRole() {
        when(userRepository.existsByEmailIgnoreCase("newuser@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = authService.createUser("New User", "newuser@example.com", "password123", Role.ADMIN);

        assertNotNull(response);
        assertEquals("newuser@example.com", response.getEmail());
        assertEquals("New User", response.getName());
        assertEquals("ADMIN", response.getRole());
        assertTrue(response.isActive());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals(Role.ADMIN, userCaptor.getValue().getRole());
    }

    @Test
    void createUser_shouldThrowExceptionIfEmailExists() {
        when(userRepository.existsByEmailIgnoreCase("existing@example.com")).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class, 
            () -> authService.createUser("Test", "existing@example.com", "password", Role.USER));
        assertEquals("Email already exists", ex.getMessage());
    }

    @Test
    void updateUserRole_shouldChangeUserRole() {
        User user = User.builder()
                .id(1L)
                .email("test@example.com")
                .name("Test User")
                .role(Role.USER)
                .active(true)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = authService.updateUserRole(1L, Role.ADMIN);

        assertNotNull(response);
        assertEquals("ADMIN", response.getRole());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals(Role.ADMIN, userCaptor.getValue().getRole());
    }

    @Test
    void updateUserRole_shouldThrowExceptionWhenUserNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, 
            () -> authService.updateUserRole(999L, Role.ADMIN));
        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void toggleUserStatus_shouldToggleActiveToInactive() {
        User user = User.builder()
                .id(1L)
                .email("test@example.com")
                .name("Test User")
                .role(Role.USER)
                .active(true)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = authService.toggleUserStatus(1L);

        assertNotNull(response);
        assertFalse(response.isActive());
    }

    @Test
    void toggleUserStatus_shouldToggleInactiveToActive() {
        User user = User.builder()
                .id(1L)
                .email("test@example.com")
                .name("Test User")
                .role(Role.USER)
                .active(false)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = authService.toggleUserStatus(1L);

        assertNotNull(response);
        assertTrue(response.isActive());
    }

    @Test
    void deleteUser_shouldDeleteUserWhenExists() {
        User user = User.builder()
                .id(1L)
                .email("test@example.com")
                .name("Test User")
                .role(Role.USER)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        authService.deleteUser(1L);

        verify(userRepository).delete(user);
    }

    @Test
    void deleteUser_shouldThrowExceptionWhenUserNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.deleteUser(999L));
        assertEquals("User not found", ex.getMessage());
    }
}