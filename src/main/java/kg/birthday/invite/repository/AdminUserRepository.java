package kg.birthday.invite.repository;

import kg.birthday.invite.entity.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {
    Optional<AdminUser> findByLoginIgnoreCase(String login);

    boolean existsByLoginIgnoreCase(String login);

    List<AdminUser> findAllByOrderByCreatedAtAsc();
}

