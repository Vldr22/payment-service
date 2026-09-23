package org.resume.paymentservice.properties;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Validated
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "verification")
public class VerificationProperties {

    @Positive(message = "Verification code TTL must be positive")
    private final int codeTtlSeconds;

    @Positive(message = "Verification max attempts must be positive")
    private final int maxAttempts;

    @Positive(message = "Verification lockout minutes must be positive")
    private final int lockoutMinutes;

}
