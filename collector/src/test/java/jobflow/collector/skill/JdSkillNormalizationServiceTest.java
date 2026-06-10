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
class JdSkillNormalizationServiceTest {

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private SkillAliasRepository skillAliasRepository;

    @Test
    @DisplayName("collector JD 텍스트에서 기준 스킬 이름을 정규화한다")
    void normalizeCanonicalSkillNames() {
        JdSkillNormalizationService service = new JdSkillNormalizationService(
                skillRepository,
                skillAliasRepository
        );
        Skill javaScript = Skill.create("JavaScript", "javascript", SkillCategory.LANGUAGE);
        Skill docker = Skill.create("Docker", "docker", SkillCategory.INFRA);

        given(skillRepository.findAllByOrderByNameAsc())
                .willReturn(List.of(docker, javaScript));
        given(skillAliasRepository.findByEnabledTrueOrderByNormalizedAliasAsc())
                .willReturn(List.of());

        List<NormalizedSkillMatch> matches = service.normalize(
                "JavaScript 기반 API와 Docker 배포 경험"
        );

        assertThat(matches)
                .extracting(match -> match.skill().getName())
                .containsExactly("Docker", "JavaScript");
    }

    @Test
    @DisplayName("collector JD 텍스트에서 alias 표현을 기준 스킬로 정규화한다")
    void normalizeSkillAliases() {
        JdSkillNormalizationService service = new JdSkillNormalizationService(
                skillRepository,
                skillAliasRepository
        );
        Skill springBoot = Skill.create("Spring Boot", "spring boot", SkillCategory.FRAMEWORK);
        Skill kubernetes = Skill.create("Kubernetes", "kubernetes", SkillCategory.INFRA);

        given(skillRepository.findAllByOrderByNameAsc())
                .willReturn(List.of(springBoot, kubernetes));
        given(skillAliasRepository.findByEnabledTrueOrderByNormalizedAliasAsc())
                .willReturn(List.of(
                        SkillAlias.create(
                                springBoot,
                                "SpringBoot",
                                "springboot",
                                BigDecimal.valueOf(0.9500)
                        ),
                        SkillAlias.create(
                                kubernetes,
                                "k8s",
                                "k8s",
                                BigDecimal.valueOf(0.9500)
                        )
                ));

        List<NormalizedSkillMatch> matches = service.normalize(
                "SpringBoot 기반 백엔드와 k8s 운영 경험"
        );

        assertThat(matches)
                .extracting(match -> match.skill().getName())
                .containsExactly("Kubernetes", "Spring Boot");
    }

    @Test
    @DisplayName("collector JD 텍스트에서 실제 공고 long-tail skill 표현을 정규화한다")
    void normalizeRealJobLongTailSkillAliases() {
        JdSkillNormalizationService service = new JdSkillNormalizationService(
                skillRepository,
                skillAliasRepository
        );
        Skill c = Skill.create("C", "c", SkillCategory.LANGUAGE);
        Skill cpp = Skill.create("C++", "c++", SkillCategory.LANGUAGE);
        Skill node = Skill.create("Node.js", "node.js", SkillCategory.FRAMEWORK);
        Skill postgres = Skill.create("PostgreSQL", "postgresql", SkillCategory.DATABASE);
        Skill rtos = Skill.create("RTOS", "rtos", SkillCategory.INFRA);
        Skill sapErp = Skill.create("SAP ERP", "sap erp", SkillCategory.TOOL);

        given(skillRepository.findAllByOrderByNameAsc())
                .willReturn(List.of(c, cpp, node, postgres, rtos, sapErp));
        given(skillAliasRepository.findByEnabledTrueOrderByNormalizedAliasAsc())
                .willReturn(List.of(
                        SkillAlias.create(node, "Node", "node", BigDecimal.valueOf(0.9000)),
                        SkillAlias.create(postgres, "Postgres", "postgres", BigDecimal.valueOf(0.9500)),
                        SkillAlias.create(rtos, "Firmware", "firmware", BigDecimal.valueOf(0.9500)),
                        SkillAlias.create(sapErp, "ERP", "erp", BigDecimal.valueOf(0.8500))
                ));

        List<NormalizedSkillMatch> matches = service.normalize(
                "C/C++ Firmware 개발과 Node, Postgres, ERP 운영 경험"
        );

        assertThat(matches)
                .extracting(match -> match.skill().getName())
                .containsExactly("C", "C++", "Node.js", "PostgreSQL", "RTOS", "SAP ERP");
    }

    @Test
    @DisplayName("collector JD 텍스트에서 security/network/hardware skill 표현을 정규화한다")
    void normalizeRealJobSecurityNetworkHardwareAliases() {
        JdSkillNormalizationService service = new JdSkillNormalizationService(
                skillRepository,
                skillAliasRepository
        );
        Skill isms = Skill.create("ISMS", "isms", SkillCategory.METHODOLOGY);
        Skill network = Skill.create("Network", "network", SkillCategory.INFRA);
        Skill rf = Skill.create("RF", "rf", SkillCategory.ETC);
        Skill spectrumAnalyzer = Skill.create("Spectrum Analyzer", "spectrum analyzer", SkillCategory.TOOL);
        Skill tcpIp = Skill.create("TCP/IP", "tcp/ip", SkillCategory.INFRA);

        given(skillRepository.findAllByOrderByNameAsc())
                .willReturn(List.of(isms, network, rf, spectrumAnalyzer, tcpIp));
        given(skillAliasRepository.findByEnabledTrueOrderByNormalizedAliasAsc())
                .willReturn(List.of(
                        SkillAlias.create(network, "네트워크", "네트워크", BigDecimal.valueOf(0.9500)),
                        SkillAlias.create(tcpIp, "TCP IP", "tcp ip", BigDecimal.valueOf(0.9500)),
                        SkillAlias.create(spectrumAnalyzer, "Spectrum", "spectrum", BigDecimal.valueOf(0.8500))
                ));

        List<NormalizedSkillMatch> matches = service.normalize(
                "ISMS 기반 네트워크 보안과 TCP/IP, RF Spectrum analyzer 사용 경험"
        );

        assertThat(matches)
                .extracting(match -> match.skill().getName())
                .containsExactly("ISMS", "Network", "RF", "Spectrum Analyzer", "TCP/IP");
    }

    @Test
    @DisplayName("collector JD 텍스트가 비어 있으면 repository를 조회하지 않는다")
    void normalizeBlankText() {
        JdSkillNormalizationService service = new JdSkillNormalizationService(
                skillRepository,
                skillAliasRepository
        );

        List<NormalizedSkillMatch> matches = service.normalize(" ", null);

        assertThat(matches).isEmpty();

        verify(skillRepository, never()).findAllByOrderByNameAsc();
        verify(skillAliasRepository, never()).findByEnabledTrueOrderByNormalizedAliasAsc();
    }
}
