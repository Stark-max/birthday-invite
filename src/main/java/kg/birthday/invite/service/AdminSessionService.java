package kg.birthday.invite.service;

import jakarta.servlet.http.HttpSession;
import kg.birthday.invite.entity.AdminUser;
import kg.birthday.invite.entity.Event;
import kg.birthday.invite.enums.AdminRole;
import kg.birthday.invite.repository.AdminUserRepository;
import kg.birthday.invite.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AdminSessionService {

    public static final String USER_ID = "adminUserId";
    public static final String ROLE = "adminRole";
    public static final String CURRENT_EVENT_ID = "currentEventId";

    private final AdminUserRepository adminUserRepository;
    private final EventRepository eventRepository;

    public void signIn(HttpSession session, AdminUser user) {
        session.setAttribute(USER_ID, user.getId());
        session.setAttribute(ROLE, user.getRole().name());
        session.removeAttribute("adminAuthenticated");
        eventRepository.findFirstByOwnerAdminIdOrderByIdAsc(user.getId())
                .ifPresent(event -> session.setAttribute(CURRENT_EVENT_ID, event.getId()));
    }

    public void selectEvent(HttpSession session, Long eventId) {
        session.setAttribute(CURRENT_EVENT_ID, eventId);
    }

    @Transactional(readOnly = true)
    public Optional<AdminUser> currentUser(HttpSession session) {
        Object rawId = session.getAttribute(USER_ID);
        if (!(rawId instanceof Number number)) {
            return Optional.empty();
        }
        return adminUserRepository.findById(number.longValue()).filter(AdminUser::isEnabled);
    }

    @Transactional(readOnly = true)
    public Optional<Event> currentEvent(HttpSession session) {
        Optional<AdminUser> userOptional = currentUser(session);
        if (userOptional.isEmpty()) {
            return Optional.empty();
        }
        AdminUser user = userOptional.get();
        if (user.isMustChangePassword()) {
            return Optional.empty();
        }
        Long eventId = number(session.getAttribute(CURRENT_EVENT_ID));
        if (eventId == null && user.getRole() == AdminRole.ADMIN) {
            Optional<Event> ownedEvent = eventRepository.findFirstByOwnerAdminIdOrderByIdAsc(user.getId());
            ownedEvent.ifPresent(event -> session.setAttribute(CURRENT_EVENT_ID, event.getId()));
            return ownedEvent;
        }
        if (eventId == null) {
            return Optional.empty();
        }
        return eventRepository.findById(eventId)
                .filter(event -> user.getRole() == AdminRole.SUPER_ADMIN
                        || event.getOwnerAdmin() != null
                        && Objects.equals(event.getOwnerAdmin().getId(), user.getId()));
    }

    public boolean isAuthenticated(HttpSession session) {
        return currentUser(session).isPresent();
    }

    public boolean isSuperAdmin(HttpSession session) {
        return currentUser(session)
                .map(AdminUser::getRole)
                .filter(AdminRole.SUPER_ADMIN::equals)
                .isPresent();
    }

    private static Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}
