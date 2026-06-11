package jobflow.domain.job;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JdRequirementSectionExtractor {

    private static final Pattern REQUIRED_HEADER_PATTERN = Pattern.compile(
            "(?:\\[\\s*)?(자격\\s*요건|지원\\s*자격|필수\\s*요건|required|requirements?|qualifications?)(?:\\s*])?",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PREFERRED_HEADER_PATTERN = Pattern.compile(
            "(?:\\[\\s*)?(우대\\s*사항|우대\\s*조건|preferred|nice\\s*to\\s*have)(?:\\s*])?",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern OTHER_HEADER_PATTERN = Pattern.compile(
            "(?:\\[\\s*)?(회사\\s*소개|주요\\s*업무|담당\\s*업무|혜택\\s*및\\s*복지|복지|채용\\s*절차|근무\\s*지역|포지션\\s*상세\\s*정보)(?:\\s*])?",
            Pattern.CASE_INSENSITIVE
    );

    public List<SkillRequirementSection> extract(String... texts) {
        String text = join(texts);

        if (text.isBlank()) {
            return List.of();
        }

        List<SectionHeader> headers = findHeaders(text);

        if (headers.stream().noneMatch(SectionHeader::isRequirementHeader)) {
            return List.of(new SkillRequirementSection(RequirementType.REQUIRED, text));
        }

        List<SkillRequirementSection> sections = new ArrayList<>();

        for (int index = 0; index < headers.size(); index++) {
            SectionHeader header = headers.get(index);

            if (!header.isRequirementHeader()) {
                continue;
            }

            int contentStart = header.end();
            int contentEnd = index + 1 < headers.size()
                    ? headers.get(index + 1).start()
                    : text.length();

            String content = text.substring(contentStart, contentEnd).trim();

            if (!content.isBlank()) {
                sections.add(new SkillRequirementSection(header.requirementType(), content));
            }
        }

        if (sections.isEmpty()) {
            return List.of(new SkillRequirementSection(RequirementType.REQUIRED, text));
        }

        return sections;
    }

    private List<SectionHeader> findHeaders(String text) {
        List<SectionHeader> headers = new ArrayList<>();
        headers.addAll(findRequirementHeaders(text, REQUIRED_HEADER_PATTERN, RequirementType.REQUIRED));
        headers.addAll(findRequirementHeaders(text, PREFERRED_HEADER_PATTERN, RequirementType.PREFERRED));
        headers.addAll(findOtherHeaders(text));
        headers.sort(Comparator.comparingInt(SectionHeader::start));
        return headers;
    }

    private List<SectionHeader> findRequirementHeaders(
            String text,
            Pattern pattern,
            RequirementType requirementType
    ) {
        Matcher matcher = pattern.matcher(text);
        List<SectionHeader> headers = new ArrayList<>();

        while (matcher.find()) {
            headers.add(new SectionHeader(
                    matcher.start(),
                    matcher.end(),
                    requirementType
            ));
        }

        return headers;
    }

    private List<SectionHeader> findOtherHeaders(String text) {
        Matcher matcher = OTHER_HEADER_PATTERN.matcher(text);
        List<SectionHeader> headers = new ArrayList<>();

        while (matcher.find()) {
            headers.add(new SectionHeader(
                    matcher.start(),
                    matcher.end(),
                    null
            ));
        }

        return headers;
    }

    private String join(String... texts) {
        if (texts == null || texts.length == 0) {
            return "";
        }

        List<String> nonBlankTexts = new ArrayList<>();

        for (String text : texts) {
            if (text != null && !text.isBlank()) {
                nonBlankTexts.add(text.trim());
            }
        }

        return String.join("\n\n", nonBlankTexts).trim();
    }

    private record SectionHeader(
            int start,
            int end,
            RequirementType requirementType
    ) {
        private boolean isRequirementHeader() {
            return requirementType != null;
        }
    }
}
