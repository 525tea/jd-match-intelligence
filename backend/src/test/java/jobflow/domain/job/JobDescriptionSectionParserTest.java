package jobflow.domain.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import jobflow.domain.job.dto.JobDescriptionSectionResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobDescriptionSectionParserTest {

    private final JobDescriptionSectionParser parser = new JobDescriptionSectionParser();

    @Test
    @DisplayName("원티드 bracket 섹션을 공통 표준 섹션으로 변환한다")
    void parseWantedBracketSections() {
        String description = """
                [회사 소개]
                채용 플랫폼을 개발합니다.

                [주요 업무]
                Spring Boot API 개발

                [자격 요건]
                Java 개발 경험

                [우대 사항]
                대용량 트래픽 경험

                [혜택 및 복지]
                자율 근무
                """;

        List<JobDescriptionSectionResponse> sections = parser.parse(description);

        assertThat(sections)
                .extracting(JobDescriptionSectionResponse::type)
                .containsExactly(
                        "COMPANY_INTRO",
                        "RESPONSIBILITIES",
                        "REQUIREMENTS",
                        "PREFERRED",
                        "BENEFITS"
                );
        assertThat(sections.get(1).title()).isEqualTo("주요 업무");
        assertThat(sections.get(1).body()).isEqualTo("Spring Boot API 개발");
        assertThat(sections.get(2).body()).isEqualTo("Java 개발 경험");
    }

    @Test
    @DisplayName("점핏 plain 섹션을 공통 표준 섹션으로 변환한다")
    void parseJumpitPlainSections() {
        String description = """
                포지션 상세 정보
                백엔드 플랫폼 개발자를 채용합니다.

                기술스택
                Java Spring Boot MySQL

                주요업무
                - API 설계 및 운영
                - 배치 안정화

                자격요건
                - Java 개발 경험

                우대사항
                - Docker 운영 경험

                채용절차 및 기타 지원 유의사항
                서류 검토 > 직무 인터뷰 > 최종 인터뷰
                """;

        List<JobDescriptionSectionResponse> sections = parser.parse(description);

        assertThat(sections)
                .extracting(JobDescriptionSectionResponse::title)
                .containsExactly(
                        "포지션 상세 정보",
                        "기술스택",
                        "주요 업무",
                        "자격 요건",
                        "우대 사항",
                        "채용절차 및 기타 지원 유의사항"
                );
        assertThat(sections.get(2).body())
                .contains("API 설계 및 운영", "배치 안정화");
        assertThat(sections.get(3).body()).contains("Java 개발 경험");
    }

    @Test
    @DisplayName("섹션 제목이 없으면 공고 원문 섹션으로 반환한다")
    void parseWithoutSectionHeading() {
        List<JobDescriptionSectionResponse> sections = parser.parse("Spring Boot 기반 API 개발자를 채용합니다.");

        assertThat(sections).hasSize(1);
        assertThat(sections.get(0).type()).isEqualTo("ORIGINAL");
        assertThat(sections.get(0).title()).isEqualTo("공고 원문");
        assertThat(sections.get(0).body()).isEqualTo("Spring Boot 기반 API 개발자를 채용합니다.");
    }

    @Test
    @DisplayName("채용전형 제목을 채용절차 표준 섹션으로 변환한다")
    void parseHiringProcessAlias() {
        String description = """
                [자격 요건]
                Java 개발 경험

                [채용전형]
                서류 검토 > 직무 인터뷰 > 최종 인터뷰
                """;

        List<JobDescriptionSectionResponse> sections = parser.parse(description);

        assertThat(sections)
                .extracting(JobDescriptionSectionResponse::type)
                .containsExactly("REQUIREMENTS", "HIRING_PROCESS");
        assertThat(sections.get(1).title()).isEqualTo("채용절차 및 기타 지원 유의사항");
        assertThat(sections.get(1).body()).contains("직무 인터뷰");
    }

    @Test
    @DisplayName("문장 중간 middle dot은 bullet로 분리하지 않는다")
    void keepInlineMiddleDotText() {
        String description = """
                [주요 업무]
                CS · 관제 · 고객을 잇는 운영 흐름을 어드민 안에서 구현
                배정/배차 어드민을 개발·운영자가 사용할 수 있게 설계
                """;

        List<JobDescriptionSectionResponse> sections = parser.parse(description);

        assertThat(sections).hasSize(1);
        assertThat(sections.get(0).body())
                .contains("CS · 관제 · 고객을 잇는 운영 흐름")
                .contains("개발·운영자가 사용할 수 있게 설계")
                .doesNotContain("• CS");
    }

    @Test
    @DisplayName("문장 중간 bullet-looking separator는 새 bullet로 분리하지 않는다")
    void keepInlineBulletLookingSeparatorText() {
        String description = """
                [주요 업무]
                CS • 관제 • 고객을 잇는 운영 흐름을 어드민 안에서 구현
                배정/배차 어드민을 개발·운영자가 사용할 수 있게 설계
                """;

        List<JobDescriptionSectionResponse> sections = parser.parse(description);

        assertThat(sections).hasSize(1);
        assertThat(sections.get(0).body())
                .contains("CS • 관제 • 고객을 잇는 운영 흐름")
                .doesNotContain("CS\n• 관제")
                .doesNotContain("관제\n• 고객");
    }

    @Test
    @DisplayName("bullet로 시작한 한 줄 안의 다음 bullet은 새 항목으로 분리한다")
    void splitInlineBulletListOnlyWhenLineStartsWithBullet() {
        String description = """
                [주요 업무]
                • 데이터 파이프라인 구성 및 관리 • 대용량 데이터 처리 • ETL 프로세스 관리
                """;

        List<JobDescriptionSectionResponse> sections = parser.parse(description);

        assertThat(sections).hasSize(1);
        assertThat(sections.get(0).body())
                .contains("• 데이터 파이프라인 구성 및 관리\n• 대용량 데이터 처리\n• ETL 프로세스 관리");
    }

    @Test
    @DisplayName("줄 시작 middle dot만 bullet로 변환한다")
    void convertLineStartMiddleDotToBullet() {
        String description = """
                [주요 업무]
                · API 설계 및 운영
                ㆍ 배치 안정화
                ﹒ 장애 대응
                """;

        List<JobDescriptionSectionResponse> sections = parser.parse(description);

        assertThat(sections).hasSize(1);
        assertThat(sections.get(0).body())
                .contains("• API 설계 및 운영")
                .contains("• 배치 안정화")
                .contains("• 장애 대응");
    }

    @Test
    @DisplayName("본문 중간의 섹션명 단어는 새 섹션으로 오인하지 않는다")
    void doNotSplitInlineSectionKeywordText() {
        String description = """
                [회사 소개]
                우리는 운영 플랫폼을 개발합니다.

                [주요 업무]
                우리 조직의 주요 업무 / 역할은 주문 배정 흐름을 안정화하는 것입니다.
                주요 업무를 수행하며 CS · 관제 · 고객을 잇는 운영 흐름을 어드민 안에서 구현합니다.

                [자격 요건]
                Java 개발 경험
                """;

        List<JobDescriptionSectionResponse> sections = parser.parse(description);

        assertThat(sections)
                .extracting(JobDescriptionSectionResponse::type)
                .containsExactly("COMPANY_INTRO", "RESPONSIBILITIES", "REQUIREMENTS");

        assertThat(sections.get(1).body())
                .contains("우리 조직의 주요 업무 / 역할")
                .contains("주요 업무를 수행하며 CS · 관제 · 고객");
    }

    @Test
    @DisplayName("plain 섹션 제목은 단독 라인일 때만 섹션으로 인식한다")
    void parsePlainSectionHeadingOnlyWhenStandaloneLine() {
        String description = """
                주요업무
                API 설계 및 운영

                자격요건
                Java 개발 경험
                """;

        List<JobDescriptionSectionResponse> sections = parser.parse(description);

        assertThat(sections)
                .extracting(JobDescriptionSectionResponse::type)
                .containsExactly("RESPONSIBILITIES", "REQUIREMENTS");
        assertThat(sections.get(0).body()).isEqualTo("API 설계 및 운영");
        assertThat(sections.get(1).body()).isEqualTo("Java 개발 경험");
    }
}
