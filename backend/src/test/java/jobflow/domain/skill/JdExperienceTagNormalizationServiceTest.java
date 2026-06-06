package jobflow.domain.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JdExperienceTagNormalizationServiceTest {

    @Mock
    private JdPhraseTagMappingRepository jdPhraseTagMappingRepository;

    @Test
    @DisplayName("JD phrase가 포함되면 experience tag로 정규화한다")
    void normalizeExperienceTagPhrases() {
        JdExperienceTagNormalizationService service =
                new JdExperienceTagNormalizationService(jdPhraseTagMappingRepository);

        ExperienceTagCode highTraffic = createExperienceTagCode("HIGH_TRAFFIC", "대용량 트래픽");
        ExperienceTagCode ciCd = createExperienceTagCode("CI_CD", "CI/CD");

        given(jdPhraseTagMappingRepository.findByEnabledTrueOrderByNormalizedPhraseAsc())
                .willReturn(List.of(
                        JdPhraseTagMapping.create(
                                "대용량 트래픽",
                                "대용량 트래픽",
                                highTraffic,
                                BigDecimal.valueOf(0.9500)
                        ),
                        JdPhraseTagMapping.create(
                                "CI/CD",
                                "ci/cd",
                                ciCd,
                                BigDecimal.valueOf(0.9500)
                        )
                ));

        List<NormalizedExperienceTagMatch> matches = service.normalize(
                "대용량 트래픽을 처리하는 Spring Boot API 개발",
                "CI/CD 파이프라인 운영 경험"
        );

        assertThat(matches)
                .extracting(match -> match.tagCode().getCode())
                .containsExactly("CI_CD", "HIGH_TRAFFIC");
        assertThat(matches)
                .extracting(NormalizedExperienceTagMatch::sourcePhrase)
                .containsExactly("CI/CD", "대용량 트래픽");
    }

    @Test
    @DisplayName("같은 tag code가 여러 phrase로 감지되면 confidence가 높은 mapping을 사용한다")
    void deduplicateSameTagCodeByConfidence() {
        JdExperienceTagNormalizationService service =
                new JdExperienceTagNormalizationService(jdPhraseTagMappingRepository);

        ExperienceTagCode highTraffic = createExperienceTagCode("HIGH_TRAFFIC", "대용량 트래픽");

        given(jdPhraseTagMappingRepository.findByEnabledTrueOrderByNormalizedPhraseAsc())
                .willReturn(List.of(
                        JdPhraseTagMapping.create(
                                "고부하 서비스",
                                "고부하 서비스",
                                highTraffic,
                                BigDecimal.valueOf(0.9000)
                        ),
                        JdPhraseTagMapping.create(
                                "대용량 트래픽",
                                "대용량 트래픽",
                                highTraffic,
                                BigDecimal.valueOf(0.9500)
                        )
                ));

        List<NormalizedExperienceTagMatch> matches = service.normalize(
                "대용량 트래픽 환경의 고부하 서비스 운영 경험"
        );

        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst().tagCode().getCode()).isEqualTo("HIGH_TRAFFIC");
        assertThat(matches.getFirst().sourcePhrase()).isEqualTo("대용량 트래픽");
    }

    @Test
    @DisplayName("영문 phrase는 대소문자를 무시하고 정규화한다")
    void normalizeAsciiPhraseCaseInsensitive() {
        JdExperienceTagNormalizationService service =
                new JdExperienceTagNormalizationService(jdPhraseTagMappingRepository);

        ExperienceTagCode monitoring = createExperienceTagCode("MONITORING", "모니터링");

        given(jdPhraseTagMappingRepository.findByEnabledTrueOrderByNormalizedPhraseAsc())
                .willReturn(List.of(
                        JdPhraseTagMapping.create(
                                "APM",
                                "apm",
                                monitoring,
                                BigDecimal.valueOf(0.8000)
                        )
                ));

        List<NormalizedExperienceTagMatch> matches = service.normalize(
                "APM 기반 장애 추적 경험"
        );

        assertThat(matches)
                .extracting(match -> match.tagCode().getCode())
                .containsExactly("MONITORING");
    }

    @Test
    @DisplayName("빈 텍스트는 repository를 조회하지 않고 빈 결과를 반환한다")
    void normalizeBlankText() {
        JdExperienceTagNormalizationService service =
                new JdExperienceTagNormalizationService(jdPhraseTagMappingRepository);

        List<NormalizedExperienceTagMatch> matches = service.normalize(" ", null);

        assertThat(matches).isEmpty();

        verify(jdPhraseTagMappingRepository, never()).findByEnabledTrueOrderByNormalizedPhraseAsc();
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
