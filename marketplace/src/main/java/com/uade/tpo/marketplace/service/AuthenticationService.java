
package com.uade.tpo.marketplace.service;

import java.math.BigDecimal;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uade.tpo.marketplace.controllers.auth.AuthenticationRequest;
import com.uade.tpo.marketplace.controllers.auth.AuthenticationResponse;
import com.uade.tpo.marketplace.controllers.auth.RegisterRequest;
import com.uade.tpo.marketplace.controllers.config.JwtService;
import com.uade.tpo.marketplace.entity.basic.User;
import com.uade.tpo.marketplace.entity.enums.Role;
import com.uade.tpo.marketplace.exceptions.BadRequestException;
import com.uade.tpo.marketplace.entity.dto.response.PasswordChangeResponseDto;
import com.uade.tpo.marketplace.exceptions.ResourceNotFoundException;
import com.uade.tpo.marketplace.exceptions.UserDuplicateException;
import com.uade.tpo.marketplace.repository.interfaces.IUserRepository;
import com.uade.tpo.marketplace.service.interfaces.IAuthenticationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthenticationService implements IAuthenticationService{

        private final IUserRepository repository;
        private final PasswordEncoder passwordEncoder;
        private final JwtService jwtService;
        private final AuthenticationManager authenticationManager;

        @Transactional (rollbackFor = Throwable.class)
        @Override
        public AuthenticationResponse register(RegisterRequest request) {

                if (request == null) throw new BadRequestException("Solicitud nula");
                if (request.getRole() != Role.BUYER && request.getRole() != Role.SELLER) {
                        throw new BadRequestException("El registro solo admite roles BUYER o SELLER.");
                }

                if (repository.existsByEmailIgnoreCase(request.getEmail())) {
                        throw new UserDuplicateException("Ya existe un usuario con ese email");
                }
                if (repository.existsByDisplayNameIgnoreCase(request.getDisplayName())) {
                        throw new UserDuplicateException("DisplayName en uso");
                }
                var user = new User(
                                request.getDisplayName(),
                                request.getFirstName(),
                                request.getLastName(),
                                request.getEmail(),
                                passwordEncoder.encode(request.getPassword()),
                                request.getRole(),
                                request.getPhone(),
                                request.getSellerDescription(),
                                request.getCountry()
                        );
                user.setActive(true);
                user.setBuyerBalance(BigDecimal.ZERO);
                user.setLastLogin(java.time.Instant.now());

                repository.save(user);
                var jwtToken = jwtService.generateToken( user);
                return AuthenticationResponse.builder()
                                .accessToken(jwtToken)
                                .build();
        }

        @Override
        public AuthenticationResponse authenticate(AuthenticationRequest request) {
                authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(
                                                request.getEmail(),
                                                request.getPassword()));
                var user = repository.findByEmail(request.getEmail())
                                .orElseThrow();
                var jwtToken = jwtService.generateToken(user);
                user.setLastLogin(java.time.Instant.now());
                repository.save(user);
                return AuthenticationResponse.builder()
                                .accessToken(jwtToken)
                                .build();
        }

        @Override
        public PasswordChangeResponseDto changePassword(Long userId, String currentPassword, String newPassword)
                throws ResourceNotFoundException {
                var user = repository.findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found with id=" + userId));

                if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                        throw new IllegalArgumentException("La contraseña actual no es correcta");
                }

                if (passwordEncoder.matches(newPassword, user.getPassword())) {
                        throw new IllegalArgumentException("La nueva contraseña no puede ser igual a la actual");
                }

                if (newPassword == null || newPassword.length() < 8) {
                        throw new IllegalArgumentException("La nueva contraseña debe tener al menos 8 caracteres");
                }

                user.setPassword(passwordEncoder.encode(newPassword));


                repository.save(user);

                return new PasswordChangeResponseDto(true, "Contraseña cambiada con éxito", userId);
        }

        
}
