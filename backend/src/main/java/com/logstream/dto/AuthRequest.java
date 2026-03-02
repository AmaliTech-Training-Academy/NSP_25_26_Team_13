package com.logstream.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor
public class AuthRequest {

    @NotBlank @Email
    private String email;

    @NotBlank
    private String password;

    private String fullName;
}
