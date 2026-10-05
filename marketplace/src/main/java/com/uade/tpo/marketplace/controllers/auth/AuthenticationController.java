package com.uade.tpo.marketplace.controllers.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uade.tpo.marketplace.entity.dto.request.ChangePasswordRequestDto;
import com.uade.tpo.marketplace.entity.dto.response.PasswordChangeResponseDto;
import com.uade.tpo.marketplace.exceptions.ResourceNotFoundException;
import com.uade.tpo.marketplace.exceptions.UserNotFoundException;
import com.uade.tpo.marketplace.repository.interfaces.IUserRepository;
import com.uade.tpo.marketplace.service.interfaces.IAuthenticationService;

import jakarta.validation.Valid;


@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {

    @Autowired
    private IAuthenticationService service;
    @Autowired
    private IUserRepository userRepository;

    @PostMapping("/register")
    public ResponseEntity<AuthenticationResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(service.register(request));
    }

    @PostMapping("/authenticate")
    public ResponseEntity<AuthenticationResponse> authenticate(
            @RequestBody AuthenticationRequest request) {
        return ResponseEntity.ok(service.authenticate(request));
    }

    @PostMapping("/change-password")
    public ResponseEntity<PasswordChangeResponseDto> changePassword(
            @Valid @RequestBody ChangePasswordRequestDto body,
            Authentication authentication
    ) throws ResourceNotFoundException {

        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String email = authentication.getName();

        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email=" + email));
        Long userId = user.getId();

        PasswordChangeResponseDto response = service.changePassword(userId, body.getCurrentPassword(), body.getNewPassword());

        return ResponseEntity.ok(response);
    }


}