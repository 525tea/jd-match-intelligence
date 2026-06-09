package jobflow.domain.job;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JdJobRoleClassificationServiceTest {

    private final JdJobRoleClassificationService service = new JdJobRoleClassificationService();

    @Test
    @DisplayName("백엔드 JD를 BACKEND로 분류한다")
    void classifyBackendRole() {
        JobRole role = service.classify(
                "백엔드 개발자",
                "Spring Boot 기반 서버 API 개발",
                "Java, JPA"
        );

        assertThat(role).isEqualTo(JobRole.BACKEND);
    }

    @Test
    @DisplayName("프론트엔드 JD를 FRONTEND로 분류한다")
    void classifyFrontendRole() {
        JobRole role = service.classify(
                "프론트엔드 개발자",
                "React 기반 웹 서비스 개발",
                "TypeScript"
        );

        assertThat(role).isEqualTo(JobRole.FRONTEND);
    }

    @Test
    @DisplayName("풀스택 JD는 backend/frontend 키워드보다 FULLSTACK을 우선한다")
    void classifyFullstackRoleFirst() {
        JobRole role = service.classify(
                "풀스택 개발자",
                "React와 Spring Boot 기반 서비스 개발"
        );

        assertThat(role).isEqualTo(JobRole.FULLSTACK);
    }

    @Test
    @DisplayName("Kubernetes 플랫폼 JD를 DEVOPS로 분류한다")
    void classifyDevopsRole() {
        JobRole role = service.classify(
                "플랫폼 엔지니어",
                "Kubernetes 기반 인프라 운영과 배포 자동화"
        );

        assertThat(role).isEqualTo(JobRole.DEVOPS);
    }

    @Test
    @DisplayName("명시된 role이 ETC가 아니면 자동 분류보다 우선한다")
    void keepProvidedRoleWhenSpecific() {
        JobRole role = service.resolve(
                JobRole.FRONTEND,
                "Spring Boot 백엔드 API 개발"
        );

        assertThat(role).isEqualTo(JobRole.FRONTEND);
    }

    @Test
    @DisplayName("JD 텍스트로 분류할 수 없으면 명시된 role을 유지한다")
    void keepProvidedRoleWhenTextIsUnknown() {
        JobRole role = service.resolve(
                JobRole.FRONTEND,
                "서비스 화면 개선과 사용자 경험 고도화"
        );

        assertThat(role).isEqualTo(JobRole.FRONTEND);
    }

    @Test
    @DisplayName("명시된 role이 ETC면 JD 텍스트 기반으로 보정한다")
    void inferRoleWhenProvidedRoleIsEtc() {
        JobRole role = service.resolve(
                JobRole.ETC,
                "iOS 앱 개발자",
                "Swift 기반 모바일 앱 개발"
        );

        assertThat(role).isEqualTo(JobRole.IOS);
    }

    @Test
    @DisplayName("분류할 수 없는 JD는 ETC로 둔다")
    void classifyUnknownRoleAsEtc() {
        JobRole role = service.classify(
                "커뮤니티 매니저",
                "사용자 커뮤니케이션과 운영"
        );

        assertThat(role).isEqualTo(JobRole.ETC);
    }

    @Test
    @DisplayName("firmware/embedded 계열은 EMBEDDED_SOFTWARE로 분류한다")
    void classifyFirmwareRole() {
        JobRole role = service.classify(
                "Application UI FW개발자",
                "NVR PC SW 및 firmware 개발"
        );

        assertThat(role).isEqualTo(JobRole.EMBEDDED_SOFTWARE);
    }

    @Test
    @DisplayName("ERP/SAP JD를 ERP_SAP으로 분류한다")
    void classifyErpSapRole() {
        JobRole role = service.classify(
                "SAP ERP기술컨설팅(SI/SM)",
                "SAP SM 모듈 운영과 ERP 시스템 유지보수"
        );

        assertThat(role).isEqualTo(JobRole.ERP_SAP);
    }

    @Test
    @DisplayName("robotics JD를 ROBOT_SOFTWARE로 분류한다")
    void classifyRobotSoftwareRole() {
        JobRole role = service.classify(
                "ROS 기반 주행 SW 개발",
                "Mobile Robot control software 개발"
        );

        assertThat(role).isEqualTo(JobRole.ROBOT_SOFTWARE);
    }

    @Test
    @DisplayName("회로/안테나 JD를 HARDWARE_ENGINEER로 분류한다")
    void classifyHardwareEngineerRole() {
        JobRole role = service.classify(
                "Phased Array Antenna Design 채용",
                "RF 회로개발"
        );

        assertThat(role).isEqualTo(JobRole.HARDWARE_ENGINEER);
    }

    @Test
    @DisplayName("붙어 있는 FrontendEngineer 표현을 FRONTEND로 분류한다")
    void classifyJoinedFrontendEngineerRole() {
        JobRole role = service.classify(
                "FrontendEngineer(Next.js-Typescript)",
                "React 기반 웹 서비스 개발"
        );

        assertThat(role).isEqualTo(JobRole.FRONTEND);
    }

    @Test
    @DisplayName("게임 서버 JD를 GAME_SERVER로 분류한다")
    void classifyGameServerRole() {
        JobRole role = service.classify(
                "게임개발·서버",
                "대규모 게임 서버 개발"
        );

        assertThat(role).isEqualTo(JobRole.GAME_SERVER);
    }

    @Test
    @DisplayName("사이버보안 JD는 네트워크 키워드보다 SECURITY를 우선한다")
    void classifyCyberSecurityRoleBeforeNetwork() {
        JobRole role = service.classify(
                "해양 사이버보안 전문가",
                "네트워크 보안 시스템 진단"
        );

        assertThat(role).isEqualTo(JobRole.SECURITY);
    }

    @Test
    @DisplayName("AI 개발자 JD는 embedded 키워드보다 AI_ENGINEER를 우선한다")
    void classifyAiDeveloperRoleBeforeEmbedded() {
        JobRole role = service.classify(
                "AI 개발자 (경력5년↑)",
                "embedded device firmware와 AI 모델 연동 개발"
        );

        assertThat(role).isEqualTo(JobRole.AI_ENGINEER);
    }

    @Test
    @DisplayName("Frontend Engineer JD는 security/backend 키워드보다 FRONTEND를 우선한다")
    void classifyFrontendRoleBeforeSecurityAndBackend() {
        JobRole role = service.classify(
                "Frontend Engineer (React/Security)",
                "보안 솔루션의 backend API 연동 화면 개발"
        );

        assertThat(role).isEqualTo(JobRole.FRONTEND);
    }

    @Test
    @DisplayName("서비스 백엔드 JD는 React 키워드보다 BACKEND를 우선한다")
    void classifyBackendRoleBeforeFullstack() {
        JobRole role = service.classify(
                "서비스 백엔드 개발자 | Node.js · React · 운영 자동화",
                "React 기반 관리 화면과 Node.js API 운영"
        );

        assertThat(role).isEqualTo(JobRole.BACKEND);
    }

    @Test
    @DisplayName("UAV Autonomy JD는 embedded 키워드보다 AUTONOMOUS_DRIVING을 우선한다")
    void classifyAutonomousRoleBeforeEmbedded() {
        JobRole role = service.classify(
                "항공 및 로보틱스 엔지니어 (UAV Autonomy)",
                "embedded controller 기반 자율주행 알고리즘 개발"
        );

        assertThat(role).isEqualTo(JobRole.AUTONOMOUS_DRIVING);
    }

    @Test
    @DisplayName("PM JD는 frontend 키워드보다 PM을 우선한다")
    void classifyPmRoleBeforeFrontend() {
        JobRole role = service.classify(
                "Project Management Officer",
                "frontend 개발 조직의 프로젝트 관리"
        );

        assertThat(role).isEqualTo(JobRole.PM);
    }
}
