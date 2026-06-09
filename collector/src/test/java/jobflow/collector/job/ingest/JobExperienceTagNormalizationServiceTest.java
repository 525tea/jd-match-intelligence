package jobflow.collector.job.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import jobflow.collector.job.CareerLevel;
import jobflow.collector.job.EmploymentType;
import jobflow.collector.job.Job;
import jobflow.collector.job.JobExperienceTag;
import jobflow.collector.job.JobExperienceTagRepository;
import jobflow.collector.job.JobRole;
import jobflow.collector.job.RemoteType;
import jobflow.collector.skill.ExperienceTagCode;
import jobflow.collector.skill.JdExperienceTagNormalizationService;
import jobflow.collector.skill.NormalizedExperienceTagMatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class JobExperienceTagNormalizationServiceTest {

    @Mock
    private JdExperienceTagNormalizationService jdExperienceTagNormalizationService;

    @Mock
    private JobExperienceTagRepository jobExperienceTagRepository;

    @Test
    @DisplayName("기존 job_experience_tags를 지우고 정규화된 experience tag를 저장한다")
    void replaceNormalizedExperienceTags() {
        JobExperienceTagNormalizationService service = new JobExperienceTagNormalizationService(
                jdExperienceTagNormalizationService,
                jobExperienceTagRepository
        );
        Job job = createJob(1L);
        ExperienceTagCode highTraffic = ExperienceTagCode.create(
                "HIGH_TRAFFIC",
                "대용량 트래픽",
                "대용량 트래픽 처리 경험"
        );
        ExperienceTagCode ciCd = ExperienceTagCode.create(
                "CI_CD",
                "CI/CD",
                "빌드, 테스트, 배포 자동화 경험"
        );
        JobExperienceTag highTrafficJobTag = JobExperienceTag.create(job, highTraffic, "대용량 트래픽");
        JobExperienceTag ciCdJobTag = JobExperienceTag.create(job, ciCd, "CI/CD");

        given(jdExperienceTagNormalizationService.normalize(
                "백엔드 개발자",
                "대용량 트래픽 처리 경험",
                "CI/CD 운영"
        )).willReturn(List.of(
                new NormalizedExperienceTagMatch(
                        highTraffic,
                        "대용량 트래픽",
                        BigDecimal.valueOf(0.9500)
                ),
                new NormalizedExperienceTagMatch(
                        ciCd,
                        "CI/CD",
                        BigDecimal.valueOf(0.9500)
                )
        ));
        given(jobExperienceTagRepository.saveAll(org.mockito.ArgumentMatchers.anyList()))
                .willReturn(List.of(highTrafficJobTag, ciCdJobTag));

        List<JobExperienceTag> jobExperienceTags = service.replaceNormalizedExperienceTags(
                job,
                "백엔드 개발자",
                "대용량 트래픽 처리 경험",
                "CI/CD 운영"
        );

        assertThat(jobExperienceTags)
                .extracting(jobExperienceTag -> jobExperienceTag.getTagCode().getCode())
                .containsExactly("HIGH_TRAFFIC", "CI_CD");

        verify(jobExperienceTagRepository).deleteByJobId(1L);
        verify(jobExperienceTagRepository).flush();
        verify(jobExperienceTagRepository).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("정규화된 experience tag가 없어도 기존 job_experience_tags는 비운다")
    void replaceNormalizedExperienceTagsWithNoMatches() {
        JobExperienceTagNormalizationService service = new JobExperienceTagNormalizationService(
                jdExperienceTagNormalizationService,
                jobExperienceTagRepository
        );
        Job job = createJob(1L);

        given(jdExperienceTagNormalizationService.normalize("백엔드 개발자"))
                .willReturn(List.of());

        List<JobExperienceTag> jobExperienceTags = service.replaceNormalizedExperienceTags(job, "백엔드 개발자");

        assertThat(jobExperienceTags).isEmpty();

        verify(jobExperienceTagRepository).deleteByJobId(1L);
        verify(jobExperienceTagRepository).flush();
    }

    private Job createJob(Long id) {
        Job job = Job.create(
                "ZIGHANG",
                "zighang-experience-tag-normalization-test",
                "백엔드 개발자",
                "JobFlow",
                "대용량 트래픽 처리 경험",
                "https://zighang.com/jobs/zighang-experience-tag-normalization-test",
                JobRole.BACKEND,
                "CI/CD 운영",
                CareerLevel.JUNIOR,
                0,
                3,
                "학력무관",
                EmploymentType.FULL_TIME,
                "STARTUP",
                "IT",
                "KR",
                "Seoul",
                "Gangnam",
                RemoteType.HYBRID,
                4000,
                7000,
                "KRW",
                true,
                1,
                LocalDateTime.of(2026, 6, 4, 9, 0),
                LocalDateTime.of(2026, 7, 1, 23, 59)
        );
        ReflectionTestUtils.setField(job, "id", id);
        return job;
    }
}
