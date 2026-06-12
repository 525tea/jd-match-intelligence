package jobflow.domain.job;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JdRequirementSectionExtractorTest {

    private final JdRequirementSectionExtractor extractor = new JdRequirementSectionExtractor();

    @Test
    @DisplayName("브래킷 섹션 제목으로 required와 preferred 구간을 분리한다")
    void extractBracketedSections() {
        assertThat(extractor.extract(
                """
                [회사 소개]
                좋은 회사입니다.

                [주요 업무]
                API 개발

                [자격 요건]
                Java Spring Boot 경험
                MySQL 사용 경험

                [우대 사항]
                Redis 운영 경험
                Kafka 사용 경험

                [혜택 및 복지]
                점심 제공
                """
        ))
                .extracting(
                        SkillRequirementSection::requirementType,
                        SkillRequirementSection::text
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                RequirementType.REQUIRED,
                                "Java Spring Boot 경험\nMySQL 사용 경험"
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                RequirementType.PREFERRED,
                                "Redis 운영 경험\nKafka 사용 경험"
                        )
                );
    }

    @Test
    @DisplayName("일반 한글 섹션 제목으로 required와 preferred 구간을 분리한다")
    void extractKoreanSections() {
        assertThat(extractor.extract(
                """
                주요업무
                백엔드 API 개발

                자격요건
                Java 개발 경험

                우대사항
                Kubernetes 운영 경험

                채용절차
                서류 전형
                """
        ))
                .extracting(
                        SkillRequirementSection::requirementType,
                        SkillRequirementSection::text
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                RequirementType.REQUIRED,
                                "Java 개발 경험"
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                RequirementType.PREFERRED,
                                "Kubernetes 운영 경험"
                        )
                );
    }

    @Test
    @DisplayName("영문 섹션 제목도 required와 preferred로 분리한다")
    void extractEnglishSections() {
        assertThat(extractor.extract(
                """
                Responsibilities
                Build backend APIs

                Requirements
                Java and Spring Boot

                Nice to have
                Redis and Kafka
                """
        ))
                .extracting(
                        SkillRequirementSection::requirementType,
                        SkillRequirementSection::text
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                RequirementType.REQUIRED,
                                "Java and Spring Boot"
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                RequirementType.PREFERRED,
                                "Redis and Kafka"
                        )
                );
    }

    @Test
    @DisplayName("섹션 제목이 없으면 전체 텍스트를 required로 취급한다")
    void fallbackToRequiredWhenNoSectionExists() {
        assertThat(extractor.extract("Java Spring Boot Redis"))
                .extracting(
                        SkillRequirementSection::requirementType,
                        SkillRequirementSection::text
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                RequirementType.REQUIRED,
                                "Java Spring Boot Redis"
                        )
                );
    }

    @Test
    @DisplayName("빈 텍스트는 빈 섹션 목록을 반환한다")
    void extractBlankText() {
        assertThat(extractor.extract(null, " ", "")).isEmpty();
    }
}
