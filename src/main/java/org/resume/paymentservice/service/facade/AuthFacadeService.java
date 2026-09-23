package org.resume.paymentservice.service.facade;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.resume.paymentservice.model.dto.request.*;
import org.resume.paymentservice.model.dto.response.ClientResponse;
import org.resume.paymentservice.model.dto.response.EmployeeResponse;
import org.resume.paymentservice.model.dto.response.TokenResponse;
import org.resume.paymentservice.model.entity.Staff;
import org.resume.paymentservice.model.entity.User;
import org.resume.paymentservice.security.JwtBlacklistService;
import org.resume.paymentservice.security.JwtCookeService;
import org.resume.paymentservice.security.JwtService;
import org.resume.paymentservice.service.verification.VerificationCodeService;
import org.resume.paymentservice.service.user.StaffService;
import org.resume.paymentservice.service.user.UserService;
import org.resume.paymentservice.utils.CodeGenerator;
import org.resume.paymentservice.utils.PhoneUtils;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthFacadeService {

    private final UserService userService;
    private final StaffService staffService;
    private final JwtService jwtService;
    private final JwtCookeService jwtCookeService;
    private final JwtBlacklistService jwtBlacklistService;
    private final VerificationCodeService verificationCodeService;

    // ========== CLIENT ==============
    public ClientResponse registerClient(ClientRegistrationRequest request) {
        String normalizedPhone = PhoneUtils.normalize(request.phone());
        User user = createClient(request, normalizedPhone);
        log.info("Client registered: phone={}", user.getPhone());
        return toClientResponse(user);
    }

    public void sendSmsCode(SmsCodeRequest request) {
        String normalizedPhone = PhoneUtils.normalize(request.phone());
        userService.getUserByPhone(normalizedPhone);
        verificationCodeService.sendCode(normalizedPhone);
        log.info("SMS code sent: phone={}", normalizedPhone);
    }

    public TokenResponse verifySmsAndLoginClient(ClientLoginRequest request, HttpServletResponse response) {
        String normalizedPhone = PhoneUtils.normalize(request.phone());
        verificationCodeService.verifyCode(normalizedPhone, request.code());

        User user = userService.getUserByPhone(normalizedPhone);
        TokenResponse tokenResponse = generateUserTokenAndSetCookie(normalizedPhone, user, response);

        log.info("Client logged in: phone={}", normalizedPhone);
        return tokenResponse;
    }

    // ========== ADMIN ==============
    /**
     * Создаёт сотрудника и возвращает временный пароль администратору.
     */
    public EmployeeResponse createEmployee(EmployeeRegistrationRequest request) {
        String tempPassword = CodeGenerator.generatePassword();
        Staff staff = createStaff(request, tempPassword);
        log.info("Employee created: email={}", staff.getEmail());
        return toStaffResponse(staff, tempPassword);
    }

    // ========== STAFF ==============
    public TokenResponse verifyAndLoginWithEmail(SupportLoginRequest request, HttpServletResponse response) {
        Staff staff = staffService.validateCredentials(request.email(), request.password());
        TokenResponse tokenResponse = generateStaffTokenAndSetCookie(request.email(), staff, response);
        log.info("Staff logged in: email={}, role={}", staff.getEmail(), staff.getRole());
        return tokenResponse;
    }

    public void setInitialPassword(SetInitialPasswordRequest request) {
        staffService.validatePassword(request.email(), request.tempPassword());
        staffService.setInitialPassword(request.email(), request.newPassword());
        log.info("Initial password set: email={}", request.email());
    }

    public void changePassword(ChangePasswordRequest request) {
        Staff staff = staffService.getCurrentStaff();
        staffService.validatePassword(staff.getEmail(), request.oldPassword());
        staffService.changePassword(staff.getEmail(), request.newPassword());
        log.info("Password changed: email={}", staff.getEmail());
    }

    // ========== LOGOUT ==============
    public void logout(String token, HttpServletResponse response) {
        String tokenId = jwtService.extractTokenId(token);
        long ttl = jwtService.calculateTtl(token);

        jwtBlacklistService.addToBlacklist(tokenId, ttl);
        jwtCookeService.clearAuthCookie(response);

        log.info("User logged out, token blacklisted: tokenId={}", tokenId);
    }

    // ========== HELPERS METHOD ==============
    private TokenResponse generateUserTokenAndSetCookie(String subject, User user, HttpServletResponse response) {
        String token = jwtService.generateToken(subject, user.getRole());
        jwtCookeService.setAuthCookie(response, token);
        return new TokenResponse(token);
    }

    private TokenResponse generateStaffTokenAndSetCookie(String subject, Staff staff, HttpServletResponse response) {
        String token = jwtService.generateToken(subject, staff.getRole());
        jwtCookeService.setAuthCookie(response, token);
        return new TokenResponse(token);
    }

    private ClientResponse toClientResponse(User user) {
        return new ClientResponse(
                user.getName(),
                user.getSurname(),
                user.getPhone(),
                user.getUserStatus());
    }

    private EmployeeResponse toStaffResponse(Staff staff, String tempPassword) {
        return new EmployeeResponse(
                staff.getName(),
                staff.getSurname(),
                staff.getEmail(),
                staff.getRole(),
                staff.getUserStatus(),
                tempPassword);
    }

    private User createClient(ClientRegistrationRequest request, String normalizedPhone) {
        return userService.createClient(
                request.name(),
                request.surname(),
                request.midname(),
                normalizedPhone);
    }

    private Staff createStaff(EmployeeRegistrationRequest request, String tempPassword) {
        return staffService.createEmployee(
                request.name(),
                request.surname(),
                request.midname(),
                request.email(),
                tempPassword);
    }

}
