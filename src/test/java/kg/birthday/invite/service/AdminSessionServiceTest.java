package kg.birthday.invite.service;

import kg.birthday.invite.entity.AdminUser;
import kg.birthday.invite.entity.Event;
import kg.birthday.invite.enums.AdminRole;
import kg.birthday.invite.repository.AdminUserRepository;
import kg.birthday.invite.repository.EventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminSessionServiceTest {

    @Test
    void ordinaryAdminCannotSelectAnotherOwnersEvent() {
        AdminUserRepository users = mock(AdminUserRepository.class);
        EventRepository events = mock(EventRepository.class);
        AdminSessionService service = new AdminSessionService(users, events);

        AdminUser adminA = user(1L, AdminRole.ADMIN, false);
        AdminUser adminB = user(2L, AdminRole.ADMIN, false);
        Event eventB = new Event();
        eventB.setId(20L);
        eventB.setOwnerAdmin(adminB);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AdminSessionService.USER_ID, 1L);
        session.setAttribute(AdminSessionService.CURRENT_EVENT_ID, 20L);
        when(users.findById(1L)).thenReturn(Optional.of(adminA));
        when(events.findById(20L)).thenReturn(Optional.of(eventB));

        assertThat(service.currentEvent(session)).isEmpty();
    }

    @Test
    void superAdminCanSelectAnyExistingEvent() {
        AdminUserRepository users = mock(AdminUserRepository.class);
        EventRepository events = mock(EventRepository.class);
        AdminSessionService service = new AdminSessionService(users, events);

        AdminUser superAdmin = user(1L, AdminRole.SUPER_ADMIN, false);
        Event event = new Event();
        event.setId(20L);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AdminSessionService.USER_ID, 1L);
        session.setAttribute(AdminSessionService.CURRENT_EVENT_ID, 20L);
        when(users.findById(1L)).thenReturn(Optional.of(superAdmin));
        when(events.findById(20L)).thenReturn(Optional.of(event));

        assertThat(service.currentEvent(session)).contains(event);
    }

    @Test
    void passwordChangeFlagBlocksEventAccess() {
        AdminUserRepository users = mock(AdminUserRepository.class);
        EventRepository events = mock(EventRepository.class);
        AdminSessionService service = new AdminSessionService(users, events);
        AdminUser admin = user(1L, AdminRole.ADMIN, true);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AdminSessionService.USER_ID, 1L);
        session.setAttribute(AdminSessionService.CURRENT_EVENT_ID, 20L);
        when(users.findById(1L)).thenReturn(Optional.of(admin));

        assertThat(service.currentEvent(session)).isEmpty();
    }

    private static AdminUser user(Long id, AdminRole role, boolean mustChangePassword) {
        AdminUser user = new AdminUser();
        user.setId(id);
        user.setRole(role);
        user.setEnabled(true);
        user.setMustChangePassword(mustChangePassword);
        return user;
    }
}
