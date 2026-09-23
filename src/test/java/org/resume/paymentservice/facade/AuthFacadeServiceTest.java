package org.resume.paymentservice.facade;

import jakarta.servlet.http.HttpServletResponse;
import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.resume.paymentservice.model.dto.request.*;
import org.resume.paymentservice.model.dto.response.ClientResponse;
import org.resume.paymentservice.model.dto.response.EmployeeResponse;
import org.resume.paymentservice.model.dto.response.TokenResponse;
import org.resume.paymentservice.model.entity.Staff;
import org.resume.paymentservice.model.entity.User;
import org.resume.paymentservice.security.JwtBlacklistService;
import org.resume.paymentservice.security.JwtCookeService;
import org.resume.paymentservice.security.JwtService;
import org.resume.paymentservice.service.facade.AuthFacadeService;
import org.resume.paymentservice.service.user.StaffService;
import org.resume.paymentservice.service.user.UserService;
import org.resume.paymentservice.service.verification.VerificationCodeService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthFacadeService — координация аутентификации клиентов и сотрудников")
class AuthFacadeServiceTest {

    private static final String PHONE = "+79001234567";
    private static final String EMAIL = "staff@gmail.com";
    private static final String TOKEN = "jwt_token_test";
    private static final String TOKEN_ID = "token_id_123";
    private static final String CODE = "123456";
    private static final String PASSWORD = "secret123";

    @Mock
    private UserService userService;

    @Mock
    private StaffService staffService;

    @Mock
    private JwtService jwtService;

    @Mock
    private JwtCookeService jwtCookeService;

    @Mock
    private JwtBlacklistService jwtBlacklistService;

    @Mock
    private VerificationCodeService verificationCodeService;

    @Mock
    private HttpServletResponse httpServletResponse;

    @InjectMocks
    private AuthFacadeService authFacadeService;

    private User user;
    private Staff staff;

    @BeforeEach
    void setUp() {
        user = Instancio.of(User.class)
                .set(field(User::getPhone), PHONE)
                .create();

        staff = Instancio.of(Staff.class)
                .set(field(Staff::getEmail), EMAIL)
                .create();
    }

    // registerClient

    /**
     * Проверяет регистрацию клиента — номер нормализуется,
     * пользователь создаётся и возвращается ответ об успехе.
     */
    @Test
    void shouldRegisterClient_andReturnClientResponse() {
        ClientRegistrationRequest request = new ClientRegistrationRequest(
                "Иван", "Иванов", "Иванович", PHONE);

        when(userService.createClient(any(), any(), any(), any())).thenReturn(user);

        ClientResponse result = authFacadeService.registerClient(request);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo(user.getName());
        verify(userService).createClient(any(), any(), any(), any());
    }

    // sendSmsCode

    /**
     * Проверяет отправку SMS кода — пользователь должен существовать,
     * после чего код отправляется на нормализованный номер.
     */
    @Test
    void shouldSendSmsCode_whenUserExists() {
        SmsCodeRequest request = new SmsCodeRequest(PHONE);

        when(userService.getUserByPhone(any())).thenReturn(user);

        authFacadeService.sendSmsCode(request);

        verify(verificationCodeService).sendCode(any());
    }

    // verifySmsAndLoginClient

    /**
     * Проверяет вход клиента по SMS коду:
     * код верифицируется, генерируется JWT токен и устанавливается в cookie.
     */
    @Test
    void shouldLoginClient_afterSmsVerification() {
        ClientLoginRequest request = new ClientLoginRequest(PHONE, CODE);

        when(userService.getUserByPhone(any())).thenReturn(user);
        when(jwtService.generateToken(any(), any())).thenReturn(TOKEN);

        TokenResponse result = authFacadeService.verifySmsAndLoginClient(request, httpServletResponse);

        assertThat(result.token()).isEqualTo(TOKEN);
        verify(verificationCodeService).verifyCode(any(), any());
        verify(jwtCookeService).setAuthCookie(any(), any());
    }

    // createEmployee

    /**
     * Проверяет создание сотрудника — генерируется временный пароль,
     * сотрудник сохраняется и возвращается ответ с его данными.
     */
    @Test
    void shouldCreateEmployee_andReturnEmployeeResponse() {
        EmployeeRegistrationRequest request = new EmployeeRegistrationRequest(
                "Иван", "Иванов", "Иванович", EMAIL);

        when(staffService.createEmployee(any(), any(), any(), any(), any())).thenReturn(staff);

        EmployeeResponse result = authFacadeService.createEmployee(request);

        assertThat(result).isNotNull();
        assertThat(result.email()).isEqualTo(staff.getEmail());
        assertThat(result.tempPassword()).isNotBlank();
        verify(staffService).createEmployee(any(), any(), any(), any(), any());
    }

    // verifyAndLoginWithEmail

    /**
     * Проверяет вход сотрудника по email и паролю, — credentials валидируются,
     * токен генерируется и устанавливается в cookie.
     */
    @Test
    void shouldLoginStaff_whenCredentialsValid() {
        SupportLoginRequest request = new SupportLoginRequest(EMAIL, PASSWORD);

        when(staffService.validateCredentials(EMAIL, PASSWORD)).thenReturn(staff);
        when(jwtService.generateToken(any(), any())).thenReturn(TOKEN);

        TokenResponse result = authFacadeService.verifyAndLoginWithEmail(request, httpServletResponse);

        assertThat(result.token()).isEqualTo(TOKEN);
        verify(jwtCookeService).setAuthCookie(any(), any());
    }

    // setInitialPassword

    /**
     * Проверяет установку начального пароля — временный пароль валидируется и заменяется новым.
     */
    @Test
    void shouldSetInitialPassword_whenTempPasswordValid() {
        SetInitialPasswordRequest request = new SetInitialPasswordRequest(EMAIL, PASSWORD, "newPass123");

        authFacadeService.setInitialPassword(request);

        verify(staffService).validatePassword(EMAIL, PASSWORD);
        verify(staffService).setInitialPassword(EMAIL, "newPass123");
    }

    // changePassword

    /**
     * Проверяет смену пароля текущим сотрудником, — старый пароль проходит верификацию и устанавливается новый.
     */
    @Test
    void shouldChangePassword_whenOldPasswordValid() {
        ChangePasswordRequest request = new ChangePasswordRequest(PASSWORD, "newPass123");

        when(staffService.getCurrentStaff()).thenReturn(staff);

        authFacadeService.changePassword(request);

        verify(staffService).validatePassword(EMAIL, PASSWORD);
        verify(staffService).changePassword(EMAIL, "newPass123");
    }

    // logout

    /**
     * Проверяет выход из системы — токен добавляется в blacklist и cookie очищается.
     */
    @Test
    void shouldLogout_andBlacklistToken() {
        when(jwtService.extractTokenId(TOKEN)).thenReturn(TOKEN_ID);
        when(jwtService.calculateTtl(TOKEN)).thenReturn(22L);

        authFacadeService.logout(TOKEN, httpServletResponse);

        verify(jwtBlacklistService).addToBlacklist(TOKEN_ID, 22L);
        verify(jwtCookeService).clearAuthCookie(httpServletResponse);
    }
}
