package jobflow.domain.job;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JobApplyUrlResolverTest {

    private final JobApplyUrlResolver resolver = new JobApplyUrlResolver();

    @Test
    void usesOriginalUrlFirst() {
        Job job = job("WANTED", "123");
        ReflectionTestUtils.setField(job, "originalUrl", "https://company.example.com/jobs/backend");
        ReflectionTestUtils.setField(job, "url", "https://www.wanted.co.kr/wd/123");

        assertThat(resolver.resolve(job))
                .isEqualTo("https://company.example.com/jobs/backend");
    }

    @Test
    void fallsBackToUrlWhenOriginalUrlIsBlank() {
        Job job = job("JUMPIT", "54118198");
        ReflectionTestUtils.setField(job, "originalUrl", " ");
        ReflectionTestUtils.setField(job, "url", "https://jumpit.saramin.co.kr/position/54118198");

        assertThat(resolver.resolve(job))
                .isEqualTo("https://jumpit.saramin.co.kr/position/54118198");
    }

    @Test
    void buildsWantedDetailUrlFromSourceAndExternalId() {
        assertThat(resolver.resolve("WANTED", "367438", null, null))
                .isEqualTo("https://www.wanted.co.kr/wd/367438");
    }

    @Test
    void buildsJumpitDetailUrlFromSourceAndExternalId() {
        assertThat(resolver.resolve("JUMPIT", "54118198", null, null))
                .isEqualTo("https://jumpit.saramin.co.kr/position/54118198");
    }

    @Test
    void rejectsNonHttpUrlAndFallsBackToSourceDetailUrl() {
        assertThat(resolver.resolve("WANTED", "367438", null, "javascript:alert(1)"))
                .isEqualTo("https://www.wanted.co.kr/wd/367438");
    }

    @Test
    void returnsNullWhenNoSafeUrlCanBeResolved() {
        assertThat(resolver.resolve("UNKNOWN", "abc", null, "not-a-url"))
                .isNull();
    }

    private Job job(String source, String externalId) {
        Job job = Job.create(
                source,
                externalId,
                "Backend Engineer",
                "Example Company",
                "description",
                null,
                JobRole.BACKEND,
                null,
                CareerLevel.MID,
                null,
                null,
                null,
                EmploymentType.FULL_TIME,
                null,
                null,
                "KR",
                "Seoul",
                "Gangnam",
                RemoteType.ONSITE,
                null,
                null,
                "KRW",
                false,
                null,
                null,
                null
        );
        ReflectionTestUtils.setField(job, "canonicalFingerprint", "example-company|backend-engineer|seoul");
        return job;
    }
}
