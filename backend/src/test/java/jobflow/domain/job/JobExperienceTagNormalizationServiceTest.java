package jobflow.domain.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import jobflow.domain.skill.ExperienceTagCode;
import jobflow.domain.skill.JdExperienceTagNormalizationService;
import jobflow.domain.skill.NormalizedExperienceTagMatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobExperienceTagNormalizationServiceTest {

    @Mock
    private JdExperienceTagNormalizationService jdExperienceTagNormalizationService;

    @Mock
    private JobExperienceTagRepository jobExperienceTagRepository;

    @Test
    @DisplayName("정규화된 experience tag를 job_experience_tags로 저장한다")
    void saveNormalizedExperienceTags() {
        JobExperienceTagNormalizationService service = new JobExperienceTagNormalizationService(
                jdExperienceTagNormalizationService,
                jobExperienceTagRepository
        );
        Job job = createJob();
        ExperienceTagCode highTraffic = createExperienceTagCode("HIGH_TRAFFIC", "대용량 트래픽");
        ExperienceTagCode ciCd = createExperienceTagCode("CI_CD", "CI/CD");
        JobExperienceTag highTrafficJobTag = JobExperienceTag.create(job, highTraffic, "대용량 트래픽");
        JobExperienceTag ciCdJobTag = JobExperienceTag.create(job, ciCd, "CI/CD");

        given(jdExperienceTagNormalizationService.normalize(
                "대용량 트래픽 백엔드 개발자",
                "CI/CD 파이프라인 운영 경험"
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

        List<JobExperienceTag> jobExperienceTags = service.saveNormalizedExperienceTags(
                job,
                "대용량 트래픽 백엔드 개발자",
                "CI/CD 파이프라인 운영 경험"
        );

        assertThat(jobExperienceTags)
                .extracting(jobExperienceTag -> jobExperienceTag.getTagCode().getCode())
                .containsExactly("HIGH_TRAFFIC", "CI_CD");

        assertThat(jobExperienceTags)
                .extracting(JobExperienceTag::getSourcePhrase)
                .containsExactly("대용량 트래픽", "CI/CD");

        verify(jobExperienceTagRepository).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("정규화된 experience tag가 없으면 job_experience_tags를 저장하지 않는다")
    void saveNormalizedExperienceTagsWithNoMatches() {
        JobExperienceTagNormalizationService service = new JobExperienceTagNormalizationService(
                jdExperienceTagNormalizationService,
                jobExperienceTagRepository
        );
        Job job = createJob();

        given(jdExperienceTagNormalizationService.normalize("일반 백엔드 개발 경험"))
                .willReturn(List.of());

        List<JobExperienceTag> jobExperienceTags = service.saveNormalizedExperienceTags(
                job,
                "일반 백엔드 개발 경험"
        );

        assertThat(jobExperienceTags).isEmpty();

        verify(jobExperienceTagRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    private Job createJob() {
        return Job.create(
                "MANUAL",
                "job-experience-tag-normalization-test",
                "백엔드 개발자",
                "JobFlow",
                "Spring Boot 기반 백엔드 개발",
                "https://example.com/jobs/job-experience-tag-normalization-test",
                JobRole.BACKEND,
                "Java Spring Boot",
                CareerLevel.JUNIOR,
                0,
                3,
                null,
                EmploymentType.FULL_TIME,
                null,
                "IT",
                "KR",
                "Seoul",
                "Gangnam",
                RemoteType.HYBRID,
                null,
                null,
                "KRW",
                false,
                null,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 7, 1, 23, 59)
        );
    }

    private ExperienceTagCode createExperienceTagCode(String code, String name) {
        try {
            Constructor<ExperienceTagCode> constructor = ExperienceTagCode.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            ExperienceTagCode tagCode = constructor.newInstance();
            setField(tagCode, "code", code);
            setField(tagCode, "name", name);
            setField(tagCode, "description", name + " 경험");
            setField(tagCode, "createdAt", LocalDateTime.now());
            return tagCode;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to create ExperienceTagCode for test", exception);
        }
    }

    private void setField(Object target, String fieldName, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
