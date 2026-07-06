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
    void completesChallengeOnceAndBlocksDuplicateCompletion() {
        ActivityResult first = module.processAction(
                module.getDefaultConfig(),
                Map.of("guestId", 1, "action", "complete", "challengeIndex", 0),
                List.of()
        );
        ActivityResultEntity previous = result(10L, 1L, first.getData());

        ActivityResult duplicate = module.processAction(
                module.getDefaultConfig(),
                Map.of("guestId", 1, "action", "complete", "challengeIndex", 0),
                List.of(previous)
        );

        assertThat(first.isSuccess()).isTrue();
        assertThat(first.getPoints()).isEqualTo(5);
        assertThat(duplicate.isSuccess()).isFalse();
    }

    @Test
    void rejectsUnknownChallengeIndex() {
        ActivityResult result = module.processAction(module.getDefaultConfig(), Map.of("challengeIndex", 99), List.of());

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void voteAddsPointToCompletedChallengeOwner() {
        ActivityResult complete = module.processAction(
                module.getDefaultConfig(),
                Map.of("guestId", 1, "action", "complete", "challengeIndex", 0),
                List.of()
        );
        ActivityResultEntity completedResult = result(10L, 1L, complete.getData());

        ActivityResult vote = module.processAction(
                module.getDefaultConfig(),
                Map.of("guestId", 2, "action", "vote", "targetResultId", 10),
                List.of(completedResult)
        );

        assertThat(vote.isSuccess()).isTrue();
        assertThat(vote.getPoints()).isEqualTo(1);
        assertThat(vote.getData()).containsEntry("scoreGuestId", 1L);
        assertThat(vote.getData()).containsEntry("voterGuestId", 2L);
    }

    @Test
    void blocksSelfVoteAndDuplicateVote() {
        ActivityResult complete = module.processAction(
                module.getDefaultConfig(),
                Map.of("guestId", 1, "action", "complete", "challengeIndex", 0),
                List.of()
        );
        ActivityResultEntity completedResult = result(10L, 1L, complete.getData());

        ActivityResult selfVote = module.processAction(
                module.getDefaultConfig(),
                Map.of("guestId", 1, "action", "vote", "targetResultId", 10),
                List.of(completedResult)
        );

        ActivityResult vote = module.processAction(
                module.getDefaultConfig(),
                Map.of("guestId", 2, "action", "vote", "targetResultId", 10),
                List.of(completedResult)
        );
        ActivityResultEntity previousVote = result(11L, 1L, vote.getData());

        ActivityResult duplicateVote = module.processAction(
                module.getDefaultConfig(),
                Map.of("guestId", 2, "action", "vote", "targetResultId", 10),
                List.of(completedResult, previousVote)
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
