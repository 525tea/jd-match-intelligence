package jobflow.domain.userjob;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class UserJobStatusTest {

    @Test
    @DisplayName("UserJob은 사용자 행동 상태만 관리한다")
    void userJobStatusContainsOnlyUserActionStates() {
        assertThat(UserJobStatus.values())
                .containsExactly(
                        UserJobStatus.VIEWED,
                        UserJobStatus.SAVED,
                        UserJobStatus.IGNORED
                );
    }

    @Test
    @DisplayName("APPLIED는 Application 도메인의 책임으로 두고 UserJob 상태에 포함하지 않는다")
    void userJobStatusDoesNotContainApplied() {
        boolean containsApplied = Arrays.stream(UserJobStatus.values())
                .anyMatch(status -> status.name().equals("APPLIED"));

        assertThat(containsApplied).isFalse();
    }
}
