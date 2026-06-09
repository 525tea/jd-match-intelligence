package jobflow.domain.job;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
    @DisplayName("명시된 role보다 JD 텍스트 기반 분류가 더 구체적이면 보정한다")
    void inferRoleFromTextBeforeProvidedRole() {
        JobRole role = service.resolve(
                JobRole.FRONTEND,
                "Spring Boot 백엔드 API 개발"
        );

        assertThat(role).isEqualTo(JobRole.BACKEND);
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
}
