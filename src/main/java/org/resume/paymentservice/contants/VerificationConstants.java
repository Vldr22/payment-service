package org.resume.paymentservice.contants;

import lombok.experimental.UtilityClass;

@UtilityClass
public class VerificationConstants {
    public static final String CODE_PREFIX = "verification:";
    public static final long CODE_TTL_SECONDS = 300;
    public static final String CODE_MESSAGE_FORMAT = "Код подтверждения: %s";
}
