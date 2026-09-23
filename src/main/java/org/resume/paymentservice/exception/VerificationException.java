package org.resume.paymentservice.exception;

import lombok.Getter;
import org.resume.paymentservice.utils.ErrorMessages;

@Getter
public class VerificationException extends RuntimeException {

    private final String identifier;

    private VerificationException(String message, String identifier) {
        super(message);
        this.identifier = identifier;
    }

    public static VerificationException smsCodeExpired(String phone) {
        return new VerificationException(
                String.format("%s%s", ErrorMessages.SMS_CODE_EXPIRED, phone),
                phone
        );
    }

    public static VerificationException smsCodeInvalid(String phone) {
        return new VerificationException(
                String.format("%s%s", ErrorMessages.SMS_CODE_INVALID, phone),
                phone
        );
    }

    public static VerificationException smsCodeAttemptsExceeded(String phone) {
        return new VerificationException(
                String.format("%s%s", ErrorMessages.SMS_CODE_ATTEMPTS_EXCEEDED, phone),
                phone
        );
    }
}
