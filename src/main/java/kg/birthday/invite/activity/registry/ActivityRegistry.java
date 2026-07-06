package kg.birthday.invite.activity.registry;

import kg.birthday.invite.activity.ActivityModule;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class ActivityRegistry {

    @Getter
    private final List<ActivityModule> modules;

    public ActivityRegistry(List<ActivityModule> modules) {
        this.modules = modules.stream()
                .sorted(Comparator.comparing(ActivityModule::getSlug))
                .toList();
    }

    public Optional<ActivityModule> getBySlug(String slug) {
        return modules.stream()
                .filter(module -> module.getSlug().equals(slug))
                .findFirst();
    }
}
