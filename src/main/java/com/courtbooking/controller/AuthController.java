package com.courtbooking.controller;

import com.courtbooking.config.AppUserPrincipal;
import com.courtbooking.config.JwtUtil;
import com.courtbooking.dto.AuthDtos.AuthResponse;
import com.courtbooking.dto.AuthDtos.LoginRequest;
import com.courtbooking.dto.AuthDtos.RegisterRequest;
import com.courtbooking.model.AppUser;
import com.courtbooking.model.Role;
import com.courtbooking.repository.AppUserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    public AuthController(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder,
                           AuthenticationManager authenticationManager, JwtUtil jwtUtil) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (appUserRepository.existsByEmail(request.email())) {
            return ResponseEntity.status(409).build();
        }
        AppUser user = new AppUser(request.fullName(), request.email(),
                passwordEncoder.encode(request.password()), Role.PUBLIC_USER);
        appUserRepository.save(user);

        String token = jwtUtil.generateToken(user.getEmail());
        return ResponseEntity.ok(AuthResponse.from(token, user));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        AppUser user = appUserRepository.findByEmail(request.email()).orElseThrow();
        String token = jwtUtil.generateToken(user.getEmail());
        return ResponseEntity.ok(AuthResponse.from(token, user));
    }
}
