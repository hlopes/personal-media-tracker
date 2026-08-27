package org.hlopes.auth.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hlopes.auth.dto.LoginResponse;
import org.hlopes.auth.dto.UserResponse;
import org.hlopes.auth.entity.User;
import org.hlopes.auth.mapper.UserMapper;
import org.hlopes.auth.repository.UserRepository;
import org.hlopes.config.ApplicationConfig;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class AuthService {

    @Inject
    ApplicationConfig applicationConfig;

    @Inject
    UserRepository userRepository;

    @Inject
    PasswordService passwordService;

    @Inject
    JwtService jwtService;

    @Inject
    EmailService emailService;

    @Inject
    UserMapper userMapper;

    public UserResponse getUserOrNotFound(String email) {
        return userRepository
                .findByEmail(email)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new NotFoundException("user not found"));
    }

    public User getUserOrNull(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }

    @Transactional
    public void register(String rawEmail, String rawPassword) {
        String email = normalizeEmail(rawEmail);

        if (userRepository.existsByEmail(email)) {
            throw new WebApplicationException(Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", "email already registered"))
                    .build());
        }

        User user = new User();
        user.email = email;
        user.passwordHash = passwordService.hash(rawPassword);
        user.verified = false;
        user.verificationToken = UUID.randomUUID().toString();
        user.verificationTokenExpiry =
                Instant.now().plusSeconds(applicationConfig.verification().tokenExpiryHours() * 3600);

        userRepository.persist(user);

        Log.infof("Registered user %s with token %s", email, user.verificationToken);
        emailService.sendVerificationEmail(email, user.verificationToken);
    }

    @Transactional
    public void verify(String token) {
        User user = userRepository
                .findByVerificationToken(token)
                .orElseThrow(() -> new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "invalid verification token"))
                        .build()));

        if (user.verificationTokenExpiry != null && user.verificationTokenExpiry.isBefore(Instant.now())) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "verification token expired, please resend"))
                    .build());
        }

        if (user.verified) {
            // idempotent
            user.verificationToken = null;
            user.verificationTokenExpiry = null;

            return;
        }

        user.verified = true;
        user.verificationToken = null;
        user.verificationTokenExpiry = null;
        userRepository.persist(user);
        Log.infof("Verified user %s", user.email);
    }

    @Transactional
    public void resendVerification(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        User user = userRepository
                .findByEmail(email)
                .orElseThrow(() -> new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "user not found"))
                        .build()));

        if (user.verified) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "user already verified"))
                    .build());
        }

        user.verificationToken = UUID.randomUUID().toString();
        user.verificationTokenExpiry =
                Instant.now().plusSeconds(applicationConfig.verification().tokenExpiryHours() * 3600);
        userRepository.persist(user);

        emailService.sendVerificationEmail(email, user.verificationToken);
    }

    public LoginResponse login(String rawEmail, String rawPassword) {
        String email = normalizeEmail(rawEmail);
        User user = userRepository
                .findByEmail(email)
                .orElseThrow(() -> new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED)
                        .entity(Map.of("error", "invalid credentials"))
                        .build()));

        if (!user.verified) {
            throw new WebApplicationException(Response.status(403)
                    .entity(Map.of("error", "email not verified", "code", "VERIFICATION_REQUIRED"))
                    .build());
        }

        if (!passwordService.verify(rawPassword, user.passwordHash)) {
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "invalid credentials"))
                    .build());
        }

        String token = jwtService.generateToken(user.email);

        return new LoginResponse(token, jwtService.getLifespanSeconds());
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
