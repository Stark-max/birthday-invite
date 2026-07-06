package kg.birthday.invite.activity.modules;

import kg.birthday.invite.activity.ActivityResult;
import kg.birthday.invite.entity.ActivityResultEntity;
import kg.birthday.invite.entity.Guest;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PhotoChallengeModuleTest {

    private final PhotoChallengeModule module = new PhotoChallengeModule();

    @Test
    void defaultConfigContainsFiftyMemeTemplates() {
        List<?> memes = (List<?>) module.getDefaultConfig().get("memes");

        assertThat(memes).hasSize(50);
        assertThat(memes)
                .allSatisfy(item -> assertThat((String) ((Map<?, ?>) item).get("imageUrl")).startsWith("https://i.imgflip.com/"));
    }

    @Test
    void assignsUniqueMemeCardsAcrossGuests() {
        ActivityResult first = module.processAction(
                module.getDefaultConfig(),
                Map.of("guestId", 1, "action", "assign"),
                List.of()
        );
        ActivityResultEntity firstAssignment = result(10L, 1L, first.getData());

        ActivityResult second = module.processAction(
                module.getDefaultConfig(),
                Map.of("guestId", 2, "action", "assign"),
                List.of(firstAssignment)
        );

        assertThat(first.isSuccess()).isTrue();
        assertThat(second.isSuccess()).isTrue();
        assertThat(second.getData().get("memeId")).isNotEqualTo(first.getData().get("memeId"));
        assertThat((String) first.getData().get("memeImageUrl")).startsWith("https://i.imgflip.com/");
    }

    @Test
    void blocksDuplicateAssignmentForSameGuest() {
        ActivityResult first = module.processAction(
                module.getDefaultConfig(),
                Map.of("guestId", 1, "action", "assign"),
                List.of()
        );
        ActivityResultEntity previous = result(10L, 1L, first.getData());

        ActivityResult duplicate = module.processAction(
                module.getDefaultConfig(),
                Map.of("guestId", 1, "action", "assign"),
                List.of(previous)
        );

        assertThat(duplicate.isSuccess()).isFalse();
    }

    @Test
    void submitsCaptionOnlyAfterAssignment() {
        ActivityResult noAssignment = module.processAction(
                module.getDefaultConfig(),
                Map.of("guestId", 1, "action", "submit", "caption", "Когда торт уже близко"),
                List.of()
        );
        ActivityResult assignment = module.processAction(
                module.getDefaultConfig(),
                Map.of("guestId", 1, "action", "assign"),
                List.of()
        );
        ActivityResultEntity assigned = result(10L, 1L, assignment.getData());

        ActivityResult submit = module.processAction(
                module.getDefaultConfig(),
                Map.of("guestId", 1, "action", "submit", "caption", "Когда торт уже близко"),
                List.of(assigned)
        );

        assertThat(noAssignment.isSuccess()).isFalse();
        assertThat(submit.isSuccess()).isTrue();
        assertThat(submit.getPoints()).isEqualTo(5);
        assertThat(submit.getData()).containsEntry("action", "submit");
        assertThat(submit.getData()).containsEntry("caption", "Когда торт уже близко");
    }

    @Test
    void voteAddsPointToSubmittedMemeOwner() {
        ActivityResult assignment = module.processAction(
                module.getDefaultConfig(),
                Map.of("guestId", 1, "action", "assign"),
                List.of()
        );
        ActivityResultEntity assigned = result(10L, 1L, assignment.getData());
        ActivityResult submit = module.processAction(
                module.getDefaultConfig(),
                Map.of("guestId", 1, "action", "submit", "caption", "Лучший мем вечера"),
                List.of(assigned)
        );
        ActivityResultEntity submitted = result(11L, 1L, submit.getData());

        ActivityResult vote = module.processAction(
                module.getDefaultConfig(),
                Map.of("guestId", 2, "action", "vote", "targetResultId", 11),
                List.of(assigned, submitted)
        );

        assertThat(vote.isSuccess()).isTrue();
        assertThat(vote.getPoints()).isEqualTo(1);
        assertThat(vote.getData()).containsEntry("scoreGuestId", 1L);
        assertThat(vote.getData()).containsEntry("voterGuestId", 2L);
    }

    @Test
    void blocksSelfVoteAndDuplicateVote() {
        ActivityResult assignment = module.processAction(
                module.getDefaultConfig(),
                Map.of("guestId", 1, "action", "assign"),
                List.of()
        );
        ActivityResultEntity assigned = result(10L, 1L, assignment.getData());
        ActivityResult submit = module.processAction(
                module.getDefaultConfig(),
                Map.of("guestId", 1, "action", "submit", "caption", "Лучший мем вечера"),
                List.of(assigned)
        );
        ActivityResultEntity submitted = result(11L, 1L, submit.getData());

        ActivityResult selfVote = module.processAction(
                module.getDefaultConfig(),
                Map.of("guestId", 1, "action", "vote", "targetResultId", 11),
                List.of(assigned, submitted)
        );
        ActivityResult vote = module.processAction(
                module.getDefaultConfig(),
                Map.of("guestId", 2, "action", "vote", "targetResultId", 11),
                List.of(assigned, submitted)
        );
        ActivityResultEntity previousVote = result(12L, 1L, vote.getData());
        ActivityResult duplicateVote = module.processAction(
                module.getDefaultConfig(),
                Map.of("guestId", 2, "action", "vote", "targetResultId", 11),
                List.of(assigned, submitted, previousVote)
        );

        assertThat(selfVote.isSuccess()).isFalse();
        assertThat(duplicateVote.isSuccess()).isFalse();
    }

    private static ActivityResultEntity result(Long id, Long guestId, Map<String, Object> data) {
        Guest guest = new Guest();
        guest.setId(guestId);

        ActivityResultEntity result = new ActivityResultEntity();
        result.setId(id);
        result.setGuest(guest);
        result.setResultData(new LinkedHashMap<>(data));
        return result;
    }
}
