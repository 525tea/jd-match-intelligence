package jobflow.domain.skill;

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
    @DisplayName("기준 스킬 이름이 JD에 포함되면 해당 스킬로 정규화한다")
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
                "JavaScript 기반 API와 Docker 배포 경험이 필요합니다."
        );

        assertThat(matches)
                .extracting(match -> match.skill().getName())
                .containsExactly("Docker", "JavaScript");
    }

    @Test
    @DisplayName("alias 표현을 기준 스킬로 정규화한다")
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
    @DisplayName("같은 스킬이 여러 표현으로 감지되면 하나만 반환한다")
    void deduplicateSameSkillMatches() {
        JdSkillNormalizationService service = new JdSkillNormalizationService(
                skillRepository,
                skillAliasRepository
        );
        Skill springBoot = Skill.create("Spring Boot", "spring boot", SkillCategory.FRAMEWORK);

        given(skillRepository.findAllByOrderByNameAsc())
                .willReturn(List.of(springBoot));
        given(skillAliasRepository.findByEnabledTrueOrderByNormalizedAliasAsc())
                .willReturn(List.of(
                        SkillAlias.create(
                                springBoot,
                                "spring",
                                "spring",
                                BigDecimal.valueOf(0.8000)
                        )
                ));

        List<NormalizedSkillMatch> matches = service.normalize(
                "Spring Boot와 spring 기반 서비스 개발"
        );

        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst().skill().getName()).isEqualTo("Spring Boot");
        assertThat(matches.getFirst().sourceAlias()).isEqualTo("Spring Boot");
    }

    @Test
    @DisplayName("짧은 영문 alias는 단어 경계가 맞을 때만 감지한다")
    void matchShortAsciiAliasWithWordBoundary() {
        JdSkillNormalizationService service = new JdSkillNormalizationService(
                skillRepository,
                skillAliasRepository
        );
        Skill javaScript = Skill.create("JavaScript", "javascript", SkillCategory.LANGUAGE);

        given(skillRepository.findAllByOrderByNameAsc())
                .willReturn(List.of(javaScript));
        given(skillAliasRepository.findByEnabledTrueOrderByNormalizedAliasAsc())
                .willReturn(List.of(
                        SkillAlias.create(
                                javaScript,
                                "js",
                                "js",
                                BigDecimal.valueOf(0.9000)
                        )
                ));

        List<NormalizedSkillMatch> matches = service.normalize(
                "jobs 처리 경험과 js 런타임 이해"
        );

        assertThat(matches)
                .extracting(match -> match.skill().getName())
                .containsExactly("JavaScript");
    }

    @Test
    @DisplayName("실제 공고의 embedded/robotics long-tail skill 표현을 정규화한다")
    void normalizeRealJobEmbeddedSkillAliases() {
        JdSkillNormalizationService service = new JdSkillNormalizationService(
                skillRepository,
                skillAliasRepository
        );
        Skill c = Skill.create("C", "c", SkillCategory.LANGUAGE);
        Skill cpp = Skill.create("C++", "c++", SkillCategory.LANGUAGE);
        Skill rtos = Skill.create("RTOS", "rtos", SkillCategory.INFRA);
        Skill ros = Skill.create("ROS", "ros", SkillCategory.FRAMEWORK);

        given(skillRepository.findAllByOrderByNameAsc())
                .willReturn(List.of(c, cpp, rtos, ros));
        given(skillAliasRepository.findByEnabledTrueOrderByNormalizedAliasAsc())
                .willReturn(List.of(
                        SkillAlias.create(rtos, "Firmware", "firmware", BigDecimal.valueOf(0.9500)),
                        SkillAlias.create(rtos, "펌웨어", "펌웨어", BigDecimal.valueOf(0.9500))
                ));

        List<NormalizedSkillMatch> matches = service.normalize(
                "ROS 기반 로봇 제어와 C/C++ 펌웨어 개발 경험"
        );

        assertThat(matches)
                .extracting(match -> match.skill().getName())
                .containsExactly("C", "C++", "ROS", "RTOS");
    }

    @Test
    @DisplayName("실제 공고의 web/database/tool alias를 정규화한다")
    void normalizeRealJobWebAndToolAliases() {
        JdSkillNormalizationService service = new JdSkillNormalizationService(
                skillRepository,
                skillAliasRepository
        );
        Skill node = Skill.create("Node.js", "node.js", SkillCategory.FRAMEWORK);
        Skill postgres = Skill.create("PostgreSQL", "postgresql", SkillCategory.DATABASE);
        Skill react = Skill.create("React", "react", SkillCategory.FRAMEWORK);
        Skill sapErp = Skill.create("SAP ERP", "sap erp", SkillCategory.TOOL);
        Skill softwareEngineering = Skill.create(
                "Software Engineering",
                "software engineering",
                SkillCategory.METHODOLOGY
        );

        given(skillRepository.findAllByOrderByNameAsc())
                .willReturn(List.of(node, postgres, react, sapErp, softwareEngineering));
        given(skillAliasRepository.findByEnabledTrueOrderByNormalizedAliasAsc())
                .willReturn(List.of(
                        SkillAlias.create(node, "Node", "node", BigDecimal.valueOf(0.9000)),
                        SkillAlias.create(postgres, "Postgres", "postgres", BigDecimal.valueOf(0.9500)),
                        SkillAlias.create(react, "React.js", "react.js", BigDecimal.valueOf(0.9500)),
                        SkillAlias.create(sapErp, "ERP", "erp", BigDecimal.valueOf(0.8500)),
                        SkillAlias.create(softwareEngineering, "S/W", "s w", BigDecimal.valueOf(0.8000))
                ));

        List<NormalizedSkillMatch> matches = service.normalize(
                "Node 기반 S/W 개발과 React.js, Postgres, ERP 운영 경험"
        );

        assertThat(matches)
                .extracting(match -> match.skill().getName())
                .containsExactly("Node.js", "PostgreSQL", "React", "SAP ERP", "Software Engineering");
    }

    @Test
    @DisplayName("실제 공고의 security/network/hardware skill 표현을 정규화한다")
    void normalizeRealJobSecurityNetworkHardwareAliases() {
        JdSkillNormalizationService service = new JdSkillNormalizationService(
                skillRepository,
                skillAliasRepository
        );
        Skill cissp = Skill.create("CISSP", "cissp", SkillCategory.METHODOLOGY);
        Skill isms = Skill.create("ISMS", "isms", SkillCategory.METHODOLOGY);
        Skill network = Skill.create("Network", "network", SkillCategory.INFRA);
        Skill rf = Skill.create("RF", "rf", SkillCategory.ETC);
        Skill spectrumAnalyzer = Skill.create("Spectrum Analyzer", "spectrum analyzer", SkillCategory.TOOL);
        Skill tcpIp = Skill.create("TCP/IP", "tcp/ip", SkillCategory.INFRA);

        given(skillRepository.findAllByOrderByNameAsc())
                .willReturn(List.of(cissp, isms, network, rf, spectrumAnalyzer, tcpIp));
        given(skillAliasRepository.findByEnabledTrueOrderByNormalizedAliasAsc())
                .willReturn(List.of(
                        SkillAlias.create(network, "네트워크", "네트워크", BigDecimal.valueOf(0.9500)),
                        SkillAlias.create(tcpIp, "TCP IP", "tcp ip", BigDecimal.valueOf(0.9500)),
                        SkillAlias.create(spectrumAnalyzer, "Spectrum", "spectrum", BigDecimal.valueOf(0.8500))
                ));

        List<NormalizedSkillMatch> matches = service.normalize(
                "ISMS, CISSP 기반 네트워크 보안과 TCP/IP, RF Spectrum analyzer 사용 경험"
        );

        assertThat(matches)
                .extracting(match -> match.skill().getName())
                .containsExactly("CISSP", "ISMS", "Network", "RF", "Spectrum Analyzer", "TCP/IP");
    }

    @Test
    @DisplayName("빈 텍스트는 repository를 조회하지 않고 빈 결과를 반환한다")
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
