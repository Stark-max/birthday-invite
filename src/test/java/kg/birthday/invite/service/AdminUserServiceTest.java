package kg.birthday.invite.service;

import kg.birthday.invite.entity.AdminUser;
import kg.birthday.invite.enums.AdminRole;
import kg.birthday.invite.repository.AdminUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminUserServiceTest {

    @Test
    void disabledAdminCannotAuthenticate() {
        AdminUserRepository repository = mock(AdminUserRepository.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        AdminUser user = new AdminUser();
        user.setLogin("admin");
        user.setPasswordHash(encoder.encode("secret1"));
        user.setEnabled(false);
        when(repository.findByLoginIgnoreCase("admin")).thenReturn(Optional.of(user));

        AdminUserService service = new AdminUserService(repository, encoder, mock(EventService.class));

        assertThat(service.authenticate("admin", "secret1")).isEmpty();
    }

    @Test
    void newlyCreatedAdminMustChangeTemporaryPassword() {
        AdminUserRepository repository = mock(AdminUserRepository.class);
        when(repository.existsByLoginIgnoreCase("new.admin")).thenReturn(false);
        when(repository.save(any(AdminUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AdminUserService service = new AdminUserService(
                repository,
                new BCryptPasswordEncoder(),
                mock(EventService.class)
        );

        AdminUser user = service.createAdmin("New.Admin", "Новый администратор", "secret1");

        assertThat(user.getLogin()).isEqualTo("new.admin");
        assertThat(user.getRole()).isEqualTo(AdminRole.ADMIN);
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.isMustChangePassword()).isTrue();
    }

    @Test
    void superAdminPasswordSetterStoresHashAndControlsChangeFlag() {
        AdminUserRepository repository = mock(AdminUserRepository.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        AdminUser user = new AdminUser();
        user.setId(2L);
        user.setLogin("kutman");
        user.setPasswordHash(encoder.encode("oldpass"));
        user.setRole(AdminRole.ADMIN);
        user.setEnabled(true);
        user.setMustChangePassword(true);
        when(repository.findById(2L)).thenReturn(Optional.of(user));
        when(repository.save(any(AdminUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminUserService service = new AdminUserService(repository, encoder, mock(EventService.class));

        service.setPasswordBySuperAdmin(2L, "newpass1", false);

        assertThat(user.getPasswordHash()).isNotEqualTo("newpass1");
        assertThat(encoder.matches("newpass1", user.getPasswordHash())).isTrue();
        assertThat(user.isMustChangePassword()).isFalse();
    }
}
