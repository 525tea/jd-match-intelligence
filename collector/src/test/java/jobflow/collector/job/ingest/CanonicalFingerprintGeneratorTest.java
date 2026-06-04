package jobflow.collector.job.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import jobflow.collector.job.CareerLevel;
import jobflow.collector.job.EmploymentType;
import jobflow.collector.job.JobRole;
import jobflow.collector.job.RemoteType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CanonicalFingerprintGeneratorTest {

    private final CanonicalFingerprintGenerator generator = new CanonicalFingerprintGenerator();

    @Test
    @DisplayName("source와 externalId가 달라도 회사명, 제목, 위치가 같으면 같은 fingerprint를 생성한다")
    void generateSameFingerprintAcrossSources() {
        IngestedJobPosting zighang = createPosting(
                JobIngestionSource.ZIGHANG,
                "zighang-123",
                "JobFlow",
                "백엔드 개발자",
                "Seoul",
                "Gangnam"
        );
        IngestedJobPosting jumpit = createPosting(
                JobIngestionSource.JUMPIT,
                "jumpit-999",
                "Job Flow",
                "백엔드-개발자",
                "seoul",
                "gangnam"
        );

        String first = generator.generate(zighang);
        String second = generator.generate(jumpit);

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(64);
    }

    @Test
    @DisplayName("회사명이나 제목이 다르면 다른 fingerprint를 생성한다")
    void generateDifferentFingerprint() {
        IngestedJobPosting firstPosting = createPosting(
                JobIngestionSource.ZIGHANG,
                "zighang-123",
                "JobFlow",
                "백엔드 개발자",
                "Seoul",
                "Gangnam"
        );
        IngestedJobPosting secondPosting = createPosting(
                JobIngestionSource.JUMPIT,
                "jumpit-999",
                "DifferentCompany",
                "백엔드 개발자",
                "Seoul",
                "Gangnam"
        );

        assertThat(generator.generate(firstPosting))
                .isNotEqualTo(generator.generate(secondPosting));
    }

    private IngestedJobPosting createPosting(
            JobIngestionSource source,
            String externalId,
            String companyName,
            String title,
            String locationRegion,
            String locationCity
    ) {
        return new IngestedJobPosting(
                source,
                externalId,
                title,
                companyName,
                "Spring Boot 기반 백엔드 개발자를 채용합니다.",
                "https://example.com/jobs/" + externalId + "?utm=test",
                "https://example.com/jobs/" + externalId,
                JobRole.BACKEND,
                "Java/Spring",
                CareerLevel.JUNIOR,
                0,
                3,
                "학력무관",
                EmploymentType.FULL_TIME,
                "STARTUP",
                "IT",
                "KR",
                locationRegion,
                locationCity,
                RemoteType.HYBRID,
                4000,
                7000,
                "KRW",
                true,
                1,
                LocalDateTime.of(2026, 6, 4, 9, 0),
                LocalDateTime.of(2026, 7, 1, 23, 59),
                LocalDateTime.of(2026, 6, 4, 10, 0),
                LocalDateTime.of(2026, 6, 4, 10, 5),
                LocalDateTime.of(2026, 6, 4, 9, 30),
                """
                        {"titleText":"%s","companyText":"%s"}
                        """.formatted(title, companyName),
                "test-parser-0.1"
        );
    }
}
