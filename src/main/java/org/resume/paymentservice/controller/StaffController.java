package org.resume.paymentservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.resume.paymentservice.contants.ApiPaths;
import org.resume.paymentservice.model.dto.CommonResponse;
import org.resume.paymentservice.model.dto.request.ChangePasswordRequest;
import org.resume.paymentservice.service.facade.AuthFacadeService;
import org.resume.paymentservice.utils.SuccessMessages;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Staff")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.API_V1 + "/staff")
public class StaffController {

    private final AuthFacadeService authFacadeService;

    @Operation(summary = "Смена пароля",
            description = "Меняет пароль текущего сотрудника. Требует роль `ROLE_EMPLOYEE` или `ROLE_ADMIN`.")
    @PatchMapping("/password")
    public CommonResponse<String> changePassword(
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        authFacadeService.changePassword(request);
        return CommonResponse.success(SuccessMessages.UPDATE_PASSWORD_SUCCESS);
    }

}
