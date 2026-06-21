package jobflow.domain.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import jobflow.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class JobRepositoryFilterTest {

    @Autowired
    private JobRepository jobRepository;

    @Test
    @DisplayName("공고 목록 조회는 status, role, careerLevel, locationRegion, remoteType 필터를 적용한다")
    void findSummariesAppliesFilters() {
        Job matchedOld = jobRepository.save(createJob(
                "matched-old",
                JobRole.BACKEND,
                CareerLevel.JUNIOR,
                "Seoul",
                RemoteType.ONSITE
        ));
        jobRepository.save(createJob(
                "wrong-role",
                JobRole.FRONTEND,
                CareerLevel.JUNIOR,
                "Seoul",
                RemoteType.ONSITE
        ));
        jobRepository.save(createJob(
                "wrong-career",
                JobRole.BACKEND,
                CareerLevel.SENIOR,
                "Seoul",
                RemoteType.ONSITE
        ));
        jobRepository.save(createJob(
                "wrong-location",
                JobRole.BACKEND,
                CareerLevel.JUNIOR,
                "Busan",
                RemoteType.ONSITE
        ));
        jobRepository.save(createJob(
                "wrong-remote",
                JobRole.BACKEND,
                CareerLevel.JUNIOR,
                "Seoul",
                RemoteType.REMOTE
        ));

        Job closedJob = jobRepository.save(createJob(
                "closed-job",
                JobRole.BACKEND,
                CareerLevel.JUNIOR,
                "Seoul",
                RemoteType.ONSITE
        ));
        closedJob.close();

        Job matchedNew = jobRepository.save(createJob(
                "matched-new",
                JobRole.BACKEND,
                CareerLevel.JUNIOR,
                "Seoul",
                RemoteType.ONSITE
        ));

        jobRepository.flush();

        List<Job> jobs = jobRepository.findSummaries(
                JobStatus.OPEN,
                JobRole.BACKEND,
                CareerLevel.JUNIOR,
                "Seoul",
                RemoteType.ONSITE,
                PageRequest.of(0, 10)
        );

        assertThat(jobs)
                .extracting(Job::getExternalId)
                .containsExactly("matched-new", "matched-old");
        assertThat(jobs).containsExactly(matchedNew, matchedOld);
    }

    @Test
    @DisplayName("공고 목록 조회는 null 필터를 무시하고 page size를 적용한다")
    void findSummariesIgnoresNullFiltersAndAppliesPageSize() {
        jobRepository.save(createJob(
                "open-1",
                JobRole.BACKEND,
                CareerLevel.JUNIOR,
                "Seoul",
                RemoteType.ONSITE
        ));
        jobRepository.save(createJob(
                "open-2",
                JobRole.FRONTEND,
                CareerLevel.MID,
                "Incheon",
                RemoteType.HYBRID
        ));
        jobRepository.save(createJob(
                "open-3",
                JobRole.DATA_ENGINEER,
                CareerLevel.SENIOR,
                "Busan",
                RemoteType.REMOTE
        ));

        jobRepository.flush();

        List<Job> jobs = jobRepository.findSummaries(
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 2)
        );

        assertThat(jobs)
                .extracting(Job::getExternalId)
                .containsExactly("open-3", "open-2");
    }

    private Job createJob(
            String externalId,
            JobRole role,
            CareerLevel careerLevel,
            String locationRegion,
            RemoteType remoteType
    ) {
        return Job.create(
                "TEST",
                externalId,
                "Backend Engineer",
                "Example Company",
                "Spring Boot backend engineer",
                "https://example.com/jobs/" + externalId,
                role,
                "Backend",
                careerLevel,
                1,
                5,
                "BACHELOR",
                EmploymentType.FULL_TIME,
                "STARTUP",
                "IT",
                "KR",
                locationRegion,
                "Gangnam",
                remoteType,
                40000000,
                80000000,
                "KRW",
                true,
                1,
                LocalDateTime.of(2026, 6, 1, 0, 0),
                LocalDateTime.of(2026, 7, 1, 0, 0)
        );
    }
}
