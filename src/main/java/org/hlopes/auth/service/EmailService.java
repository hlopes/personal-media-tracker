package org.hlopes.auth.service;

import org.hlopes.config.ApplicationConfig;

import io.quarkus.logging.Log;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class EmailService {

    @Inject
    Mailer mailer;

    @Inject
    ApplicationConfig applicationConfig;

    public void sendVerificationEmail(String email, String token) {
        String baseUrl = applicationConfig.verification().baseUrl();
        String verificationLink = baseUrl + "/api/auth/verify?token=" + token;
        String subject = "Verify your email - Personal Media Tracker";
        String body =
                """
                Welcome to Personal Media Tracker!

                Please verify your email by clicking the link below:
                %s

                This link expires in 24 hours. If you did not create an account, please ignore this email.
                """
                        .formatted(verificationLink);

        // Always log for dev/Swagger testing (Q9: Mailpit + console fallback)
        Log.infof("Verification link for %s: %s", email, verificationLink);

        try {
            mailer.send(Mail.withText(email, subject, body));
            Log.infof("Verification email sent to %s", email);
        } catch (Exception e) {
            Log.warnf("Failed to send email to %s via mailer (fallback to log only): %s", email, e.getMessage());
            // Do not fail registration if mailer unavailable in dev; link is already logged
        }
    }
}
