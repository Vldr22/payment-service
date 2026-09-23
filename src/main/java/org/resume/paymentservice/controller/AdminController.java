package org.resume.paymentservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.resume.paymentservice.contants.ApiPaths;
import org.resume.paymentservice.model.dto.CommonResponse;
import org.resume.paymentservice.model.dto.request.EmployeeRegistrationRequest;
import org.resume.paymentservice.model.dto.response.EmployeeResponse;
import org.resume.paymentservice.service.facade.AuthFacadeService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.API_V1 + "/admin")
public class AdminController {

    private final AuthFacadeService authFacadeService;

    @Operation(summary = "Регистрация сотрудника (ROLE_EMPLOYEE)",
            description = "Создаёт аккаунт нового сотрудника или сотрудника поддержки. Требует роль `ROLE_ADMIN`.")
    @ApiResponse(responseCode = "409", description = "Email уже зарегистрирован")
    @PostMapping("/register-employee")
    public CommonResponse<EmployeeResponse> createEmployee(
            @Valid @RequestBody EmployeeRegistrationRequest request) {

        EmployeeResponse response = authFacadeService.createEmployee(request);
        return CommonResponse.success(response);
    }

}
