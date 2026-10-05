package com.uade.tpo.marketplace.controllers.auth;

import com.uade.tpo.marketplace.entity.enums.Role;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {
    @NotBlank @Size(max = 100)
    private String displayName;
    @NotBlank @Size(max = 50)
    private String firstName;
    @NotBlank @Size(max = 50)
    private String lastName;
    @NotBlank @Email @Size(max = 150)
    private String email;
    @NotBlank @Size(min = 8, max = 72)
    private String password;
    private Role role;
    @NotBlank
    private String phone;
    private String sellerDescription;
    @NotBlank
    private String country;
}
