package org.resume.paymentservice.contants;

import lombok.experimental.UtilityClass;

@UtilityClass
public class VerificationConstants {
    public static final String CODE_PREFIX = "verification:";
    public static final String ATTEMPTS_PREFIX = "verification:attempts:";
    public static final String CODE_MESSAGE_FORMAT = "Код подтверждения: %s";
}
