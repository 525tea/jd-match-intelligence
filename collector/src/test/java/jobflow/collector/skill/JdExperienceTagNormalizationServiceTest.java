package jobflow.collector.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
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
    @DisplayName("collector JD 텍스트에서 experience tag phrase를 정규화한다")
    void normalizeExperienceTagPhrases() {
        JdExperienceTagNormalizationService service =
                new JdExperienceTagNormalizationService(jdPhraseTagMappingRepository);
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
    @DisplayName("collector JD 텍스트에서 같은 tag code가 여러 번 감지되면 confidence가 높은 mapping을 사용한다")
    void deduplicateSameTagCodeByConfidence() {
        JdExperienceTagNormalizationService service =
                new JdExperienceTagNormalizationService(jdPhraseTagMappingRepository);
        ExperienceTagCode highTraffic = ExperienceTagCode.create(
                "HIGH_TRAFFIC",
                "대용량 트래픽",
                "대용량 트래픽 처리 경험"
        );

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
    @DisplayName("collector JD 텍스트가 비어 있으면 repository를 조회하지 않는다")
    void normalizeBlankText() {
        JdExperienceTagNormalizationService service =
                new JdExperienceTagNormalizationService(jdPhraseTagMappingRepository);

        List<NormalizedExperienceTagMatch> matches = service.normalize(" ", null);

        assertThat(matches).isEmpty();

        verify(jdPhraseTagMappingRepository, never()).findByEnabledTrueOrderByNormalizedPhraseAsc();
    }
}
