package jobflow.domain.job;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jobflow.domain.job.dto.JobDescriptionSectionResponse;

public class JobDescriptionSectionParser {

    private static final List<SectionDefinition> SECTION_DEFINITIONS = List.of(
            new SectionDefinition(
                    "POSITION_DETAIL",
                    "포지션 상세 정보",
                    List.of("포지션 상세 정보", "포지션 정보", "포지션 경력/학력/마감일/근무지역 정보")
            ),
            new SectionDefinition(
                    "TECH_STACK",
                    "기술스택",
                    List.of("기술스택", "기술 스택", "사용 기술", "tech stack")
            ),
            new SectionDefinition(
                    "RESPONSIBILITIES",
                    "주요 업무",
                    List.of("주요 업무", "주요업무", "담당 업무", "담당업무", "main tasks")
            ),
            new SectionDefinition(
                    "REQUIREMENTS",
                    "자격 요건",
                    List.of("자격 요건", "자격요건", "지원 자격", "지원자격", "필수 요건", "필수요건", "requirements")
            ),
            new SectionDefinition(
                    "PREFERRED",
                    "우대 사항",
                    List.of("우대 사항", "우대사항", "preferred points", "preferred qualifications")
            ),
            new SectionDefinition(
                    "BENEFITS",
                    "복지 및 혜택",
                    List.of("복지 및 혜택", "혜택 및 복지", "복리후생", "benefits")
            ),
            new SectionDefinition(
                    "HIRING_PROCESS",
                    "채용절차 및 기타 지원 유의사항",
                    List.of(
                            "채용절차 및 기타 지원 유의사항",
                            "채용 절차 및 기타 지원 유의사항",
                            "채용전형",
                            "채용 전형",
                            "채용절차",
                            "채용 절차",
                            "채용 프로세스",
                            "전형절차",
                            "전형 절차",
                            "전형 과정",
                            "지원 유의사항",
                            "기타 지원 유의사항",
                            "process"
                    )
            ),
            new SectionDefinition(
                    "COMPANY_INTRO",
                    "기업/서비스 소개",
                    List.of("기업/서비스 소개", "기업 소개", "회사 소개", "서비스 소개", "company introduction")
            ),
            new SectionDefinition(
                    "TEAM_INTRO",
                    "팀 소개",
                    List.of("팀 소개", "team introduction")
            )
    );

    private static final Pattern LINE_BREAK_MARKER_PATTERN = Pattern.compile("\\\\n");
    private static final Pattern MULTIPLE_BLANK_LINE_PATTERN = Pattern.compile("\\n{3,}");
    private static final Pattern BULLET_BOUNDARY_PATTERN = Pattern.compile("\\s+(?=(?:[-•]\\s+|\\d+\\.\\s+))");
    private static final Pattern MIDDLE_DOT_PATTERN = Pattern.compile("\\s*(?:ㆍ|·)\\s*");
    private static final Map<String, SectionDefinition> ALIASES_BY_NORMALIZED_TEXT = aliasesByNormalizedText();
    private static final Pattern SECTION_HEADING_PATTERN = sectionHeadingPattern();

    public List<JobDescriptionSectionResponse> parse(String description) {
        String normalized = normalizeDescription(description);

        if (normalized.isBlank()) {
            return List.of();
        }

        List<SectionToken> tokens = findSectionTokens(normalized);

        if (tokens.isEmpty()) {
            return List.of(new JobDescriptionSectionResponse(
                    "ORIGINAL",
                    "공고 원문",
                    normalized
            ));
        }

        List<JobDescriptionSectionResponse> sections = new ArrayList<>();
        String preface = normalized.substring(0, tokens.get(0).start()).trim();

        if (!preface.isBlank()) {
            sections.add(new JobDescriptionSectionResponse(
                    "ORIGINAL",
                    "공고 원문",
                    preface
            ));
        }

        for (int i = 0; i < tokens.size(); i++) {
            SectionToken token = tokens.get(i);
            int bodyEnd = i + 1 < tokens.size()
                    ? tokens.get(i + 1).start()
                    : normalized.length();
            String body = normalized.substring(token.end(), bodyEnd).trim();

            if (!body.isBlank()) {
                sections.add(new JobDescriptionSectionResponse(
                        token.definition().type(),
                        token.definition().title(),
                        body
                ));
            }
        }

        return sections;
    }

    private List<SectionToken> findSectionTokens(String description) {
        Matcher matcher = SECTION_HEADING_PATTERN.matcher(description);
        List<SectionToken> tokens = new ArrayList<>();

        while (matcher.find()) {
            SectionDefinition definition = findDefinition(matcher);

            if (definition != null) {
                tokens.add(new SectionToken(matcher.start(), matcher.end(), definition));
            }
        }

        return tokens;
    }

    private SectionDefinition findDefinition(Matcher matcher) {
        for (int i = 1; i <= matcher.groupCount(); i++) {
            String value = matcher.group(i);

            if (value != null && !value.isBlank()) {
                return ALIASES_BY_NORMALIZED_TEXT.get(normalizeHeading(value));
            }
        }

        return null;
    }

    private String normalizeDescription(String value) {
        String text = LINE_BREAK_MARKER_PATTERN.matcher(String.valueOf(value == null ? "" : value))
                .replaceAll("\n")
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("[ \\t]*\\n[ \\t]*", "\n");
        text = BULLET_BOUNDARY_PATTERN.matcher(text).replaceAll("\n");
        text = MIDDLE_DOT_PATTERN.matcher(text).replaceAll("\n• ");
        text = MULTIPLE_BLANK_LINE_PATTERN.matcher(text.trim()).replaceAll("\n\n");
        return text;
    }

    private static Map<String, SectionDefinition> aliasesByNormalizedText() {
        Map<String, SectionDefinition> definitions = new LinkedHashMap<>();

        for (SectionDefinition definition : SECTION_DEFINITIONS) {
            for (String alias : definition.aliases()) {
                definitions.put(normalizeHeading(alias), definition);
            }
        }

        return Map.copyOf(definitions);
    }

    private static Pattern sectionHeadingPattern() {
        String aliases = SECTION_DEFINITIONS.stream()
                .flatMap(definition -> definition.aliases().stream())
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(Pattern::quote)
                .reduce((left, right) -> left + "|" + right)
                .orElseThrow();

        return Pattern.compile(
                "(?:\\[\\s*(" + aliases + ")\\s*\\])|(?:^|\\n|\\s{1,})(" + aliases + ")(?=\\s|$)",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        );
    }

    private static String normalizeHeading(String value) {
        return value == null
                ? ""
                : value.replace("[", "")
                        .replace("]", "")
                        .trim()
                        .toLowerCase(Locale.ROOT);
    }

    private record SectionDefinition(
            String type,
            String title,
            List<String> aliases
    ) {
    }

    private record SectionToken(
            int start,
            int end,
            SectionDefinition definition
    ) {
    }
}
