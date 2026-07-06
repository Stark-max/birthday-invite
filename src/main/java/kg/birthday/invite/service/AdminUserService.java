package kg.birthday.invite.service;

import kg.birthday.invite.dto.EventSetupRequest;
import kg.birthday.invite.entity.AdminUser;
import kg.birthday.invite.entity.Event;
import kg.birthday.invite.enums.AdminRole;
import kg.birthday.invite.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final AdminUserRepository adminUserRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final EventService eventService;

    @Transactional(readOnly = true)
    public boolean hasUsers() {
        return adminUserRepository.count() > 0;
    }

    @Transactional(readOnly = true)
    public List<AdminUser> getAllUsers() {
        return adminUserRepository.findAllByOrderByCreatedAtAsc();
    }

    @Transactional
    public Optional<AdminUser> authenticate(String login, String password) {
        if (login == null || password == null) {
            return Optional.empty();
        }
        Optional<AdminUser> userOptional = adminUserRepository.findByLoginIgnoreCase(login.trim());
        if (userOptional.isEmpty()) {
            return Optional.empty();
        }
        AdminUser user = userOptional.get();
        if (!user.isEnabled() || !passwordEncoder.matches(password, user.getPasswordHash())) {
            return Optional.empty();
        }
        user.setLastLoginAt(LocalDateTime.now());
        return Optional.of(adminUserRepository.save(user));
    }

    @Transactional
    public Event createInitialSuperAdmin(EventSetupRequest request) {
        if (adminUserRepository.count() > 0) {
            throw new IllegalStateException("Application is already configured");
        }
        AdminUser user = new AdminUser();
        user.setLogin(normalizeLogin(request.getAdminLogin()));
        user.setDisplayName(requiredText(request.getAdminDisplayName(), "Super Admin"));
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(AdminRole.SUPER_ADMIN);
        user.setEnabled(true);
        user.setMustChangePassword(false);
        AdminUser saved = adminUserRepository.save(user);
        return eventService.createEvent(request, saved);
    }

    @Transactional
    public AdminUser createAdmin(String login, String displayName, String temporaryPassword) {
        String normalizedLogin = normalizeLogin(login);
        if (adminUserRepository.existsByLoginIgnoreCase(normalizedLogin)) {
            throw new IllegalArgumentException("Логин уже занят.");
        }
        if (temporaryPassword == null || temporaryPassword.length() < 6) {
            throw new IllegalArgumentException("Временный пароль должен содержать минимум 6 символов.");
        }
        AdminUser user = new AdminUser();
        user.setLogin(normalizedLogin);
        user.setDisplayName(requiredText(displayName, normalizedLogin));
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setRole(AdminRole.ADMIN);
        user.setEnabled(true);
        user.setMustChangePassword(true);
        return adminUserRepository.save(user);
    }

    @Transactional
    public void setEnabled(Long userId, boolean enabled) {
        AdminUser user = getManagedAdmin(userId);
        user.setEnabled(enabled);
        adminUserRepository.save(user);
    }

    @Transactional
    public void resetPassword(Long userId, String temporaryPassword) {
        setPasswordBySuperAdmin(userId, temporaryPassword, true);
    }

    @Transactional
    public void setPasswordBySuperAdmin(Long userId, String newPassword, boolean mustChangePassword) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("Новый пароль должен содержать минимум 6 символов.");
        }
        AdminUser user = getManagedAdmin(userId);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(mustChangePassword);
        adminUserRepository.save(user);
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        AdminUser user = adminUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Администратор не найден."));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Текущий пароль указан неверно.");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("Новый пароль должен содержать минимум 6 символов.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        adminUserRepository.save(user);
    }

    private AdminUser getManagedAdmin(Long userId) {
        AdminUser user = adminUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Администратор не найден."));
        if (user.getRole() == AdminRole.SUPER_ADMIN) {
            throw new IllegalArgumentException("Учётную запись супер-админа нельзя изменить здесь.");
        }
        return user;
    }

    private static String normalizeLogin(String login) {
        String normalized = requiredText(login, "superadmin").toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9._-]{3,120}")) {
            throw new IllegalArgumentException("Логин: 3-120 символов, только латиница, цифры, точка, дефис и подчёркивание.");
        }
        return normalized;
    }

    private static String requiredText(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
