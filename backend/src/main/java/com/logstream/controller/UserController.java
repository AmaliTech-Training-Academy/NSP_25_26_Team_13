package com.logstream.controller;

import com.logstream.dto.RegisterRequest;
import com.logstream.dto.UpdateRoleRequest;
import com.logstream.dto.UserResponse;
import com.logstream.exception.BadRequestException;
import com.logstream.model.Role;
import com.logstream.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "User Management", description = "APIs for managing users (Admin only)")
@Slf4j
public class UserController {

    private final AuthService authService;

    @GetMapping
    @Operation(summary = "Get all users", description = "Retrieve a list of all users (Admin only)")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(authService.getAllUsers());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID", description = "Retrieve a specific user by their ID (Admin only)")
    public ResponseEntity<UserResponse> getUserById(
            @Parameter(description = "User ID") @PathVariable Long id) {
        return ResponseEntity.ok(authService.getUserById(id));
    }

    @PostMapping
    @Operation(summary = "Create new user", description = "Create a new user account (Admin only)")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody RegisterRequest request) {
        Role role = parseRole(request.getRole());
        return ResponseEntity.ok(authService.createUser(
                request.getFullName(),
                request.getEmail(),
                request.getPassword(),
                role
        ));
    }

    @PutMapping("/{id}/role")
    @Operation(summary = "Update user role", description = "Update the role of a specific user (Admin only)")
    public ResponseEntity<UserResponse> updateUserRole(
            @Parameter(description = "User ID") @PathVariable Long id, 
            @Valid @RequestBody UpdateRoleRequest request) {
        Role role = parseRole(request.getRole());
        return ResponseEntity.ok(authService.updateUserRole(id, role));
    }

    @PutMapping("/{id}/toggle-status")
    @Operation(summary = "Toggle user status", description = "Toggle the active/inactive status of a user (Admin only)")
    public ResponseEntity<UserResponse> toggleUserStatus(
            @Parameter(description = "User ID") @PathVariable Long id) {
        return ResponseEntity.ok(authService.toggleUserStatus(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user", description = "Delete a user account (Admin only)")
    public ResponseEntity<Void> deleteUser(
            @Parameter(description = "User ID") @PathVariable Long id) {
        authService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    private Role parseRole(String roleStr) {
        if (roleStr == null || roleStr.isBlank()) {
            return Role.USER;
        }
        try {
            return Role.valueOf(roleStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid role '{}', defaulting to USER", roleStr);
            return Role.USER;
        }
    }
}
