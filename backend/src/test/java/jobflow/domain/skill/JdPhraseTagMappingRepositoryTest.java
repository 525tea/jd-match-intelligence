package jobflow.domain.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class JdPhraseTagMappingRepositoryTest {

    @Autowired
    private JdPhraseTagMappingRepository jdPhraseTagMappingRepository;

    @Autowired
    private ExperienceTagCodeRepository experienceTagCodeRepository;

    @Test
    @DisplayName("활성화된 JD phrase mapping 목록을 normalized phrase 기준으로 조회한다")
    void findByEnabledTrueOrderByNormalizedPhraseAsc() {
        ExperienceTagCode highTraffic = experienceTagCodeRepository.save(createExperienceTagCode(
                "HIGH_TRAFFIC",
                "대용량 트래픽",
                "대용량 트래픽 처리 경험"
        ));
        ExperienceTagCode ciCd = experienceTagCodeRepository.save(createExperienceTagCode(
                "CI_CD",
                "CI/CD",
                "빌드, 테스트, 배포 자동화 경험"
        ));

        jdPhraseTagMappingRepository.save(JdPhraseTagMapping.create(
                "대용량 트래픽",
                "대용량 트래픽",
                highTraffic,
                BigDecimal.valueOf(0.9500)
        ));
        jdPhraseTagMappingRepository.save(JdPhraseTagMapping.create(
                "CI/CD",
                "ci/cd",
                ciCd,
                BigDecimal.valueOf(0.9500)
        ));

        List<JdPhraseTagMapping> mappings =
                jdPhraseTagMappingRepository.findByEnabledTrueOrderByNormalizedPhraseAsc();

        assertThat(mappings)
                .extracting(JdPhraseTagMapping::getNormalizedPhrase)
                .containsExactly("ci/cd", "대용량 트래픽");
    }

    @Test
    @DisplayName("비활성화된 JD phrase mapping은 활성 목록에서 제외한다")
    void findByEnabledTrueOrderByNormalizedPhraseAscExcludesDisabledMapping() {
        ExperienceTagCode reliability = experienceTagCodeRepository.save(createExperienceTagCode(
                "RELIABILITY",
                "안정성",
                "장애 대응과 안정성 개선 경험"
        ));
        JdPhraseTagMapping mapping = jdPhraseTagMappingRepository.save(JdPhraseTagMapping.create(
                "장애 대응",
                "장애 대응",
                reliability,
                BigDecimal.valueOf(0.9500)
        ));
        mapping.disable();

        List<JdPhraseTagMapping> mappings =
                jdPhraseTagMappingRepository.findByEnabledTrueOrderByNormalizedPhraseAsc();

        assertThat(mappings).isEmpty();
    }

    @Test
    @DisplayName("normalized phrase와 tag code 기준으로 mapping 존재 여부를 확인한다")
    void existsByNormalizedPhraseAndTagCodeCode() {
        ExperienceTagCode performance = experienceTagCodeRepository.save(createExperienceTagCode(
                "PERFORMANCE",
                "성능 최적화",
                "성능 개선 경험"
        ));
        jdPhraseTagMappingRepository.save(JdPhraseTagMapping.create(
                "쿼리 튜닝",
                "쿼리 튜닝",
                performance,
                BigDecimal.valueOf(0.9000)
        ));

        boolean exists = jdPhraseTagMappingRepository.existsByNormalizedPhraseAndTagCodeCode(
                "쿼리 튜닝",
                "PERFORMANCE"
        );

        assertThat(exists).isTrue();
    }

    private ExperienceTagCode createExperienceTagCode(String code, String name, String description) {
        try {
            Constructor<ExperienceTagCode> constructor = ExperienceTagCode.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            ExperienceTagCode tagCode = constructor.newInstance();
            setField(tagCode, "code", code);
            setField(tagCode, "name", name);
            setField(tagCode, "description", description);
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
