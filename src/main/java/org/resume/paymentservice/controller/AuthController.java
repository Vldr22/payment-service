package org.resume.paymentservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.resume.paymentservice.contants.ApiPaths;
import org.resume.paymentservice.model.dto.CommonResponse;
import org.resume.paymentservice.model.dto.request.*;
import org.resume.paymentservice.model.dto.response.ClientResponse;
import org.resume.paymentservice.model.dto.response.TokenResponse;
import org.resume.paymentservice.service.facade.AuthFacadeService;
import org.resume.paymentservice.utils.SuccessMessages;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import static org.resume.paymentservice.contants.SecurityConstants.COOKIE_NAME;

@Tag(name = "Auth")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.API_V1 + "/auth")
public class AuthController {

    private final AuthFacadeService authFacadeService;

    // ======== CLIENT ========
    @Operation(summary = "Регистрация клиента",
            description = "Создаёт новый аккаунт клиента. Токен не возвращается — после регистрации необходима SMS верификация.")
    @ApiResponse(responseCode = "201", description = "Клиент успешно зарегистрирован")
    @ApiResponse(responseCode = "409", description = "Номер телефона уже зарегистрирован")
    @SecurityRequirements
    @PostMapping("/register-client")
    @ResponseStatus(HttpStatus.CREATED)
    public CommonResponse<ClientResponse> registerClient(
            @Valid @RequestBody ClientRegistrationRequest request
    ) {
        ClientResponse response = authFacadeService.registerClient(request);
        return CommonResponse.success(response);
    }

    @Operation(summary = "Отправка SMS кода",
            description = "Отправляет одноразовый код подтверждения на указанный номер телефона.")
    @ApiResponse(responseCode = "404", description = "Клиент с таким номером не найден")
    @SecurityRequirements
    @PostMapping("/sms/send")
    public CommonResponse<String> sendSmsCode(@Valid @RequestBody SmsCodeRequest request) {
        authFacadeService.sendSmsCode(request);
        return CommonResponse.success(SuccessMessages.SMS_CODE_SENT);
    }

    @Operation(summary = "Верификация SMS кода",
            description = "Верифицирует код. При успехе возвращает JWT токен и устанавливает его в cookie.")
    @ApiResponse(responseCode = "400", description = "Неверный или истёкший SMS код")
    @SecurityRequirements
    @PostMapping("/sms/verify-client")
    public CommonResponse<TokenResponse> verifySmsCode(
            @Valid @RequestBody ClientLoginRequest request,
            HttpServletResponse response
    ) {
        TokenResponse tokenResponse = authFacadeService.verifySmsAndLoginClient(request, response);
        return CommonResponse.success(tokenResponse);
    }

    // ======== SUPPORT ========
    @Operation(summary = "Установка начального пароля",
            description = "Первичная установка пароля для нового сотрудника, зарегистрированного администратором.")
    @ApiResponse(responseCode = "401", description = "Неверный логин или пароль")
    @ApiResponse(responseCode = "404", description = "Сотрудник не найден")
    @SecurityRequirements
    @PatchMapping("/staff/set-password")
    public CommonResponse<String> setInitialPassword(
            @Valid @RequestBody SetInitialPasswordRequest request
    ) {
        authFacadeService.setInitialPassword(request);
        return CommonResponse.success(SuccessMessages.UPDATE_PASSWORD_SUCCESS);
    }


    @Operation(summary = "Вход сотрудника",
            description = "Аутентификация по email и паролю. Возвращает JWT токен и устанавливает его в cookie.")
    @ApiResponse(responseCode = "401", description = "Неверный логин или пароль")
    @ApiResponse(responseCode = "404", description = "Сотрудник не найден")
    @SecurityRequirements
    @PostMapping("/staff/login")
    public CommonResponse<TokenResponse> loginByEmail(
            @Valid @RequestBody SupportLoginRequest request,
            HttpServletResponse response
    ) {
        TokenResponse tokenResponse = authFacadeService.verifyAndLoginWithEmail(request, response);
        return CommonResponse.success(tokenResponse);
    }

    // ======== LOGOUT ========
    @Operation(summary = "Выход",
            description = "Инвалидирует JWT токен — добавляет в blacklist в Redis. Очищает cookie.")
    @PostMapping("/logout")
    public CommonResponse<String> logout(
            @CookieValue(name = COOKIE_NAME) String token,
            HttpServletResponse response
    ) {
        authFacadeService.logout(token, response);
        return CommonResponse.success(SuccessMessages.LOGOUT_SUCCESS);
    }

}
