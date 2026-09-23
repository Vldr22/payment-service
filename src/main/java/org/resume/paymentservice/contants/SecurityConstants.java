package org.resume.paymentservice.contants;

import lombok.experimental.UtilityClass;

@UtilityClass
public class SecurityConstants {

    public static final String BLACKLIST_PREFIX = "blacklist:";
    public static final String COOKIE_NAME = "auth_token";
    public static final String CLAIM_ROLE = "role";

    public static final String MSG_UNAUTHORIZED = "Authentication required";
    public static final String MSG_ACCESS_DENIED = "Access denied";

    public static final String[] PUBLIC_PATHS = {
            ApiPaths.API_V1 + "/auth/**",
            ApiPaths.API_V1 + "/webhooks/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**"
    };
}
