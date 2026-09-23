package org.resume.paymentservice.model.enums;

public enum Roles {
    ROLE_USER,
    ROLE_EMPLOYEE,
    ROLE_ADMIN;

    /**
     * Возвращает полномочие Spring Security.
     */
    public String getAuthority() {
        return name();
    }
}