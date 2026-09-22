package org.resume.paymentservice.service.user;

import lombok.RequiredArgsConstructor;
import org.resume.paymentservice.exception.AlreadyExistsException;
import org.resume.paymentservice.exception.AuthException;
import org.resume.paymentservice.exception.NotFoundException;
import org.resume.paymentservice.model.entity.Staff;
import org.resume.paymentservice.model.enums.Roles;
import org.resume.paymentservice.repository.StaffRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StaffService {

    private final StaffRepository staffRepository;
    private final PasswordEncoder passwordEncoder;

    public Staff getByEmail(String email) {
        return staffRepository.findByEmail(email)
                .orElseThrow(() -> NotFoundException.staffByEmail(email));
    }

    public boolean existsByEmail(String email) {
        return staffRepository.existsByEmail(email);
    }

    public Staff createEmployee(String name, String surname, String midname,
                                String email, String rawPassword) {
        if (staffRepository.existsByEmail(email)) {
            throw AlreadyExistsException.staffByEmail(email);
        }
        String encoded = passwordEncoder.encode(rawPassword);
        Staff staff = new Staff(name, surname, midname, email, encoded, Roles.ROLE_EMPLOYEE, true);
        return staffRepository.save(staff);
    }

    public void validatePassword(String email, String rawPassword) {
        Staff staff = getByEmail(email);
        if (!passwordEncoder.matches(rawPassword, staff.getPassword())) {
            throw AuthException.invalidCredentials();
        }
    }

    public Staff validateCredentials(String email, String rawPassword) {
        Staff staff = getByEmail(email);
        if (!passwordEncoder.matches(rawPassword, staff.getPassword())) {
            throw AuthException.invalidCredentials();
        }
        if (staff.isPasswordChangeRequired()) {
            throw AuthException.passwordChangeRequired();
        }
        return staff;
    }

    @Transactional
    public void changePassword(String email, String newRawPassword) {
        String encoded = passwordEncoder.encode(newRawPassword);
        staffRepository.updatePassword(email, encoded);
    }

    @Transactional
    public void setInitialPassword(String email, String newRawPassword) {
        Staff staff = getByEmail(email);

        if (!staff.isPasswordChangeRequired()) {
            throw AuthException.invalidCredentials();
        }

        String encoded = passwordEncoder.encode(newRawPassword);
        staffRepository.updatePassword(email, encoded);
        staffRepository.clearPasswordChangeRequired(email);
    }

    public Staff getCurrentStaff() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();
        return getByEmail(email);
    }

}