package jobflow.domain.notification.digest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.JobRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DailyDigestSectionAssemblerTest {

    private final DailyDigestSectionAssembler assembler = new DailyDigestSectionAssembler();

    @Test
    @DisplayName("section priority 기준으로 중복 jobId를 제거한다")
    void deduplicateBySectionPriority() {
        DailyDigestContent content = assembler.assemble(
                List.of(
                        item(1L, "추천 공고 1"),
                        item(2L, "추천 공고 2"),
                        item(3L, "추천 공고 3")
                ),
                List.of(
                        item(2L, "JD 매칭 중복 공고"),
                        item(4L, "JD 매칭 공고 1"),
                        item(5L, "JD 매칭 공고 2"),
                        item(6L, "JD 매칭 공고 3")
                ),
                List.of(
                        item(1L, "신규 중복 공고"),
                        item(7L, "신규 공고")
                ),
                List.of(
                        item(4L, "마감임박 중복 공고"),
                        item(8L, "마감임박 공고 1"),
                        item(9L, "마감임박 공고 2"),
                        item(10L, "마감임박 공고 3")
                )
        );

        assertThat(content.recommendedJobs())
                .extracting(DailyDigestJobItem::jobId)
                .containsExactly(1L, 2L, 3L);
        assertThat(content.jdMatchJobs())
                .extracting(DailyDigestJobItem::jobId)
                .containsExactly(4L, 5L, 6L);
        assertThat(content.newJobs())
                .extracting(DailyDigestJobItem::jobId)
                .containsExactly(7L);
        assertThat(content.deadlineReminderJobs())
                .extracting(DailyDigestJobItem::jobId)
                .containsExactly(8L, 9L, 10L);
        assertThat(content.totalJobCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("section별 limit을 적용한다")
    void limitSectionItems() {
        DailyDigestContent content = assembler.assemble(
                List.of(
                        item(1L, "추천 공고 1"),
                        item(2L, "추천 공고 2"),
                        item(3L, "추천 공고 3"),
                        item(4L, "추천 공고 4")
                ),
                List.of(
                        item(5L, "JD 매칭 공고 1"),
                        item(6L, "JD 매칭 공고 2"),
                        item(7L, "JD 매칭 공고 3"),
                        item(8L, "JD 매칭 공고 4")
                ),
                List.of(
                        item(9L, "신규 공고 1"),
                        item(10L, "신규 공고 2")
                ),
                List.of(
                        item(11L, "마감임박 공고 1"),
                        item(12L, "마감임박 공고 2"),
                        item(13L, "마감임박 공고 3"),
                        item(14L, "마감임박 공고 4")
                )
        );

        assertThat(content.recommendedJobs()).hasSize(3);
        assertThat(content.jdMatchJobs()).hasSize(3);
        assertThat(content.newJobs()).hasSize(1);
        assertThat(content.deadlineReminderJobs()).hasSize(3);
        assertThat(content.totalJobCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("후보가 없으면 빈 digest content를 반환한다")
    void returnEmptyContent() {
        DailyDigestContent content = assembler.assemble(
                null,
                List.of(),
                null,
                List.of()
        );

        assertThat(content.isEmpty()).isTrue();
        assertThat(content.totalJobCount()).isZero();
    }

    @Test
    @DisplayName("Digest item은 최소 필수값을 검증한다")
    void validateDigestItem() {
        assertThatThrownBy(() -> item(null, "공고"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("jobId is required");

        assertThatThrownBy(() -> item(1L, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("title is required");

        assertThatThrownBy(() -> new DailyDigestJobItem(
                1L,
                "공고",
                "",
                JobRole.BACKEND,
                CareerLevel.MID,
                BigDecimal.valueOf(80),
                LocalDateTime.now(),
                "https://example.com/jobs/1",
                "테스트"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("companyName is required");
    }

    private DailyDigestJobItem item(Long jobId, String title) {
        return new DailyDigestJobItem(
                jobId,
                title,
                "Example Company",
                JobRole.BACKEND,
                CareerLevel.MID,
                BigDecimal.valueOf(80),
                LocalDateTime.of(2026, 6, 17, 12, 0),
                "https://example.com/jobs/" + jobId,
                "테스트"
        );
    }
}
