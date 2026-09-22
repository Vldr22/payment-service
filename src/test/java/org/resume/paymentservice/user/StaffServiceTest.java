package org.resume.paymentservice.user;

import org.instancio.Instancio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.resume.paymentservice.exception.AlreadyExistsException;
import org.resume.paymentservice.exception.AuthException;
import org.resume.paymentservice.exception.NotFoundException;
import org.resume.paymentservice.model.entity.Staff;
import org.resume.paymentservice.model.enums.Roles;
import org.resume.paymentservice.repository.StaffRepository;
import org.resume.paymentservice.service.user.StaffService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StaffService — управление сотрудниками и аутентификацией")
class StaffServiceTest {

    private static final String EMAIL = "staff@gmail.com";
    private static final String RAW_PASS = "secret123";
    private static final String ENCODED = "$2a$encoded_password";

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private StaffService staffService;

    private Staff staff;

    @BeforeEach
    void setUp() {
        staff = Instancio.of(Staff.class)
                .set(field(Staff::getEmail), EMAIL)
                .set(field(Staff::getPassword), ENCODED)
                .set(field(Staff::isPasswordChangeRequired), false)
                .create();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // getByEmail

    /**
     * Возвращает сотрудника по-существующему email.
     */
    @Test
    void shouldReturnStaff_whenEmailExists() {
        when(staffRepository.findByEmail(EMAIL)).thenReturn(Optional.of(staff));

        Staff result = staffService.getByEmail(EMAIL);

        assertThat(result).isEqualTo(staff);
    }

    /**
     * Бросает NotFoundException если сотрудник с таким email не найден.
     */
    @Test
    void shouldThrowNotFound_whenEmailNotExist() {
        when(staffRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> staffService.getByEmail(EMAIL))
                .isInstanceOf(NotFoundException.class);
    }

    // createEmployee

    /**
     * Проверяет создание нового сотрудника с ролью ROLE_EMPLOYEE.
     * Пароль должен быть закодирован перед сохранением.
     */
    @Test
    void shouldCreateEmployee_whenEmailNotTaken() {
        when(staffRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(RAW_PASS)).thenReturn(ENCODED);
        when(staffRepository.save(any(Staff.class))).thenAnswer(inv -> inv.getArgument(0));

        Staff result = staffService.createEmployee("Иван", "Иванов", "Иванович", EMAIL, RAW_PASS);

        assertThat(result.getRole()).isEqualTo(Roles.ROLE_EMPLOYEE);
        assertThat(result.getPassword()).isEqualTo(ENCODED);
        verify(passwordEncoder).encode(RAW_PASS);
        verify(staffRepository).save(any(Staff.class));
    }

    /**
     * Нельзя создать сотрудника с уже занятым email.
     */
    @Test
    void shouldThrowAlreadyExists_whenEmailAlreadyTaken() {
        when(staffRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() ->
                staffService.createEmployee("Иван", "Иванов", "Иванович", EMAIL, RAW_PASS)
        ).isInstanceOf(AlreadyExistsException.class);
    }

    // validateCredentials

    /**
     * Проверяет успешную валидацию логина и пароля.
     * Возвращает сотрудника если всё верно.
     */
    @Test
    void shouldReturnStaff_whenCredentialsValid() {
        when(staffRepository.findByEmail(EMAIL)).thenReturn(Optional.of(staff));
        when(passwordEncoder.matches(RAW_PASS, ENCODED)).thenReturn(true);

        Staff result = staffService.validateCredentials(EMAIL, RAW_PASS);

        assertThat(result).isEqualTo(staff);
    }

    /**
     * Бросает AuthException если пароль неверный.
     */
    @Test
    void shouldThrowAuthException_whenPasswordInvalid() {
        when(staffRepository.findByEmail(EMAIL)).thenReturn(Optional.of(staff));
        when(passwordEncoder.matches(RAW_PASS, ENCODED)).thenReturn(false);

        assertThatThrownBy(() -> staffService.validateCredentials(EMAIL, RAW_PASS))
                .isInstanceOf(AuthException.class);
    }

    /**
     * Бросает AuthException если пароль верный, но требуется его смена.
     * Такой сотрудник не должен получить доступ до смены пароля.
     */
    @Test
    void shouldThrowAuthException_whenPasswordChangeRequired() {
        staff.setPasswordChangeRequired(true);
        when(staffRepository.findByEmail(EMAIL)).thenReturn(Optional.of(staff));
        when(passwordEncoder.matches(RAW_PASS, ENCODED)).thenReturn(true);

        assertThatThrownBy(() -> staffService.validateCredentials(EMAIL, RAW_PASS))
                .isInstanceOf(AuthException.class);
    }

    // setInitialPassword

    /**
     * Проверяет успешную установку начального пароля.
     * Доступно только если у сотрудника выставлен флаг passwordChangeRequired.
     */
    @Test
    void shouldSetInitialPassword_whenChangeRequired() {
        staff.setPasswordChangeRequired(true);
        when(staffRepository.findByEmail(EMAIL)).thenReturn(Optional.of(staff));
        when(passwordEncoder.encode(RAW_PASS)).thenReturn(ENCODED);

        staffService.setInitialPassword(EMAIL, RAW_PASS);

        verify(staffRepository).updatePassword(EMAIL, ENCODED);
        verify(staffRepository).clearPasswordChangeRequired(EMAIL);
    }

    /**
     * Бросает AuthException если сотрудник пытается установить начальный пароль
     * когда флаг смены уже снят.
     */
    @Test
    void shouldThrowAuthException_whenPasswordChangeNotRequired() {
        staff.setPasswordChangeRequired(false);
        when(staffRepository.findByEmail(EMAIL)).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> staffService.setInitialPassword(EMAIL, RAW_PASS))
                .isInstanceOf(AuthException.class);
    }

    // changePassword

    /**
     * Проверяет что новый пароль кодируется и сохраняется в БД.
     */
    @Test
    void shouldChangePassword_withEncodedValue() {
        when(passwordEncoder.encode(RAW_PASS)).thenReturn(ENCODED);

        staffService.changePassword(EMAIL, RAW_PASS);

        verify(passwordEncoder).encode(RAW_PASS);
        verify(staffRepository).updatePassword(EMAIL, ENCODED);
    }

    // getCurrentStaff

    /**
     * Проверяет что метод извлекает email из SecurityContext
     * и возвращает соответствующего сотрудника из БД.
     */
    @Test
    void shouldReturnCurrentStaff_fromSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(EMAIL, null, List.of())
        );
        when(staffRepository.findByEmail(EMAIL)).thenReturn(Optional.of(staff));

        Staff result = staffService.getCurrentStaff();

        assertThat(result).isEqualTo(staff);
    }

    // existsByEmail

    /**
     * Возвращает true если сотрудник с таким email существует.
     */
    @Test
    void shouldReturnTrue_whenEmailExists() {
        when(staffRepository.existsByEmail(EMAIL)).thenReturn(true);

        boolean result = staffService.existsByEmail(EMAIL);

        assertThat(result).isTrue();
    }

// validatePassword

    /**
     * Не бросает исключений если пароль верный.
     */
    @Test
    void shouldPassValidation_whenPasswordCorrect() {
        when(staffRepository.findByEmail(EMAIL)).thenReturn(Optional.of(staff));
        when(passwordEncoder.matches(RAW_PASS, ENCODED)).thenReturn(true);

        staffService.validatePassword(EMAIL, RAW_PASS);
        // исключений не должно быть
    }

    /**
     * Бросает AuthException если пароль неверный.
     */
    @Test
    void shouldThrowAuthException_whenPasswordWrong() {
        when(staffRepository.findByEmail(EMAIL)).thenReturn(Optional.of(staff));
        when(passwordEncoder.matches(RAW_PASS, ENCODED)).thenReturn(false);

        assertThatThrownBy(() -> staffService.validatePassword(EMAIL, RAW_PASS))
                .isInstanceOf(AuthException.class);
    }

}
