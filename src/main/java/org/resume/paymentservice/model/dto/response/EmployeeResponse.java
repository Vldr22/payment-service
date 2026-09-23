package org.resume.paymentservice.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.resume.paymentservice.model.enums.Roles;
import org.resume.paymentservice.model.enums.UserStatus;

public record EmployeeResponse(
        @Schema(description = "Имя", example = "Иван")
        String name,

        @Schema(description = "Фамилия", example = "Иванов")
        String surname,

        @Schema(description = "Email", example = "ivanov@gmail.com")
        String email,

        @Schema(description = "Роль", example = "ROLE_EMPLOYEE")
        Roles role,

        @Schema(description = "Статус аккаунта", example = "ACTIVE")
        UserStatus status,

        @Schema(description = "Временный пароль, возвращается только при создании", example = "aB3xK9mP")
        String tempPassword
) {
}
