package com.motorinsurance.auth.api;

import com.motorinsurance.auth.application.AuthenticationService;
import com.motorinsurance.auth.application.RegistrationService;
import com.motorinsurance.auth.domain.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auth module's public endpoints: self-registration (Story 1.2) and login
 * (Story 1.3, issues a role-bearing JWT). No token validation/protected
 * endpoints here yet - that's the shared filter, Story 1.4's job.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final AuthenticationService authenticationService;

    public AuthController(RegistrationService registrationService, AuthenticationService authenticationService) {
        this.registrationService = registrationService;
        this.authenticationService = authenticationService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        User user = registrationService.register(request.email(), request.password());
        return new RegisterResponse(user.getId(), user.getEmail(), user.getRole());
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        String token = authenticationService.login(request.email(), request.password());
        return new LoginResponse(token);
    }
}
