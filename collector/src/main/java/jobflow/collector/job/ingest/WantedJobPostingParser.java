package jobflow.collector.job.ingest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jobflow.collector.job.CareerLevel;
import jobflow.collector.job.EmploymentType;
import jobflow.collector.job.JobRole;
import jobflow.collector.job.RemoteType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class WantedJobPostingParser implements JobPostingParser {

    private static final String CRAWLER_VERSION = "wanted-parser-0.1";
    private static final int ROLE_DETAIL_MAX_LENGTH = 100;
    private static final int MAX_REASONABLE_EXPERIENCE_YEARS = 80;
    private static final Pattern EXPERIENCE_RANGE_PATTERN =
            Pattern.compile("(?:경력\\s*:?)?\\s*(\\d+)\\s*년?\\s*[~\\-–]\\s*(\\d+)\\s*년");
    private static final Pattern EXPERIENCE_MIN_PATTERN =
            Pattern.compile("(?:경력\\s*:?)?\\s*(\\d+)\\s*년\\s*이상");

    private final ObjectMapper objectMapper;
    private final JdJobRoleClassificationService jdJobRoleClassificationService;

    @Override
    public boolean supports(JobIngestionSource source) {
        return source == JobIngestionSource.WANTED;
    }

    @Override
    public IngestedJobPosting parse(FetchedJobPosting fetchedJobPosting) {
        if (!supports(fetchedJobPosting.source())) {
            throw new JobPostingParseException(
                    "Unsupported source. source=" + fetchedJobPosting.source()
            );
        }

        JsonNode job = readJob(fetchedJobPosting);
        JsonNode detail = job.path("detail");
        JsonNode company = job.path("company");
        JsonNode address = job.path("address");

        String title = normalize(job.path("position").asText(""));
        String companyName = normalize(company.path("name").asText(""));
        String description = buildDescription(detail);

        validateRequired("title", title, fetchedJobPosting);
        validateRequired("companyName", companyName, fetchedJobPosting);
        validateRequired("description", description, fetchedJobPosting);

        String pageText = normalize(title + " " + companyName + " " + description);
        ExperienceRange experienceRange = inferExperienceRange(pageText);
        LocalDateTime now = LocalDateTime.now();

        return new IngestedJobPosting(
                fetchedJobPosting.source(),
                fetchedJobPosting.externalId(),
                title,
                companyName,
                description,
                fetchedJobPosting.sourceUrl(),
                fetchedJobPosting.detailUrl(),
                classifyRole(title, description, pageText),
                buildRoleDetail(job),
                inferCareerLevel(pageText, experienceRange),
                experienceRange.min(),
                experienceRange.max(),
                null,
                inferEmploymentType(pageText),
                null,
                normalize(company.path("industry_name").asText(null)),
                "KR",
                inferRegion(address),
                inferCity(address),
                inferRemoteType(job, pageText),
                parseSalary(job.path("annual_from")),
                parseSalary(job.path("annual_to")),
                "KRW",
                hasVisibleSalary(job),
                null,
                null,
                parseDeadlineAt(job.path("due_time").asText(null)),
                now,
                now,
                null,
                fetchedJobPosting.body(),
                CRAWLER_VERSION
        );
    }

    private JsonNode readJob(FetchedJobPosting fetchedJobPosting) {
        try {
            JsonNode root = objectMapper.readTree(fetchedJobPosting.body());
            return root.path("job");
        } catch (JacksonException exception) {
            throw new JobPostingParseException(
                    "Failed to parse Wanted JSON. externalId="
                            + fetchedJobPosting.externalId()
                            + ", error="
                            + exception.getMessage()
            );
        }
    }

    private JobRole classifyRole(String title, String description, String pageText) {
        JobRole titleRole = jdJobRoleClassificationService.classify(title);

        if (titleRole != JobRole.ETC) {
            return titleRole;
        }

        return jdJobRoleClassificationService.classify(description, pageText);
    }

    private String buildDescription(JsonNode detail) {
        List<String> parts = new ArrayList<>();

        appendIfPresent(parts, "회사 소개", detail.path("intro").asText(""));
        appendIfPresent(parts, "주요 업무", detail.path("main_tasks").asText(""));
        appendIfPresent(parts, "자격 요건", detail.path("requirements").asText(""));
        appendIfPresent(parts, "우대 사항", detail.path("preferred_points").asText(""));
        appendIfPresent(parts, "혜택 및 복지", detail.path("benefits").asText(""));
        appendIfPresent(parts, "채용절차 및 기타 지원 유의사항", firstText(
                detail,
                "hiring_process",
                "recruitment_process",
                "selection_process",
                "interview_process",
                "process",
                "caution",
                "notice"
        ));

        return String.join("\n\n", parts);
    }

    private String firstText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = normalize(node.path(fieldName).asText(""));

            if (!value.isBlank()) {
                return value;
            }
        }

        return "";
    }

    private void appendIfPresent(List<String> parts, String label, String value) {
        String normalized = normalize(value);

        if (!normalized.isBlank()) {
            parts.add("[" + label + "]\n" + normalized);
        }
    }

    private String buildRoleDetail(JsonNode job) {
        List<String> skillTags = new ArrayList<>();
        JsonNode tags = job.path("skill_tags");

        if (!tags.isArray()) {
            return null;
        }

        for (JsonNode tag : tags) {
            String title = normalize(tag.path("title").asText(""));

            if (!title.isBlank()) {
                skillTags.add(title);
            }
        }

        return skillTags.isEmpty()
                ? null
                : truncate(String.join(" ", skillTags), ROLE_DETAIL_MAX_LENGTH);
    }

    private CareerLevel inferCareerLevel(String pageText, ExperienceRange experienceRange) {
        String lower = pageText.toLowerCase();

        if (containsAny(lower, "신입", "인턴", "newcomer")) {
            return CareerLevel.NEWCOMER;
        }

        if (experienceRange.min() != null) {
            if (experienceRange.min() >= 8) {
                return CareerLevel.SENIOR;
            }

            if (experienceRange.min() >= 4) {
                return CareerLevel.MID;
            }

            return CareerLevel.JUNIOR;
        }

        if (containsAny(lower, "시니어", "senior", "리드", "lead")) {
            return CareerLevel.SENIOR;
        }

        if (containsAny(lower, "주니어", "junior")) {
            return CareerLevel.JUNIOR;
        }

        return CareerLevel.ANY;
    }

    private ExperienceRange inferExperienceRange(String pageText) {
        Matcher rangeMatcher = EXPERIENCE_RANGE_PATTERN.matcher(pageText);

        while (rangeMatcher.find()) {
            Integer min = parseExperienceYear(rangeMatcher.group(1));
            Integer max = parseExperienceYear(rangeMatcher.group(2));

            if (min == null || max == null || min > max) {
                continue;
            }

            return new ExperienceRange(min, max);
        }

        Matcher minMatcher = EXPERIENCE_MIN_PATTERN.matcher(pageText);

        while (minMatcher.find()) {
            Integer min = parseExperienceYear(minMatcher.group(1));

            if (min != null) {
                return new ExperienceRange(min, null);
            }
        }

        if (containsAny(pageText, "경력 무관", "신입 가능")) {
            return new ExperienceRange(0, null);
        }

        return new ExperienceRange(null, null);
    }

    private Integer parseExperienceYear(String value) {
        int year = Integer.parseInt(value);

        if (year < 0 || year > MAX_REASONABLE_EXPERIENCE_YEARS) {
            return null;
        }

        return year;
    }

    private EmploymentType inferEmploymentType(String pageText) {
        String lower = pageText.toLowerCase();

        if (containsAny(lower, "인턴", "intern")) {
            return EmploymentType.INTERN;
        }

        if (containsAny(lower, "계약직", "contract")) {
            return EmploymentType.CONTRACT;
        }

        return EmploymentType.FULL_TIME;
    }

    private RemoteType inferRemoteType(JsonNode job, String pageText) {
        JsonNode companyTags = job.path("company_tags");

        if (companyTags.isArray()) {
            for (JsonNode tag : companyTags) {
                String title = normalize(tag.path("title").asText(""));

                if (containsAny(title, "원격", "재택")) {
                    return RemoteType.REMOTE;
                }

                if (containsAny(title, "하이브리드")) {
                    return RemoteType.HYBRID;
                }
            }
        }

        if (containsAny(pageText.toLowerCase(), "remote", "원격", "재택")) {
            return RemoteType.REMOTE;
        }

        return RemoteType.ONSITE;
    }

    private String inferRegion(JsonNode address) {
        String location = normalize(address.path("location").asText(""));
        String fullLocation = normalize(address.path("full_location").asText(""));
        String value = location + " " + fullLocation;

        if (containsAny(value, "서울")) {
            return "Seoul";
        }

        if (containsAny(value, "경기", "판교", "성남", "분당")) {
            return "Gyeonggi";
        }

        if (containsAny(value, "부산")) {
            return "Busan";
        }

        if (containsAny(value, "인천")) {
            return "Incheon";
        }

        return location.isBlank() ? null : location;
    }

    private String inferCity(JsonNode address) {
        String value = normalize(address.path("full_location").asText(""));

        if (containsAny(value, "강남")) {
            return "Gangnam";
        }

        if (containsAny(value, "서초")) {
            return "Seocho";
        }

        if (containsAny(value, "성동")) {
            return "Seongdong";
        }

        if (containsAny(value, "판교", "분당")) {
            return "Bundang";
        }

        return null;
    }

    private Integer parseSalary(JsonNode node) {
        if (node.isNull() || node.isMissingNode()) {
            return null;
        }

        int value = node.asInt(0);

        return value > 0 ? value * 10_000_000 : null;
    }

    private boolean hasVisibleSalary(JsonNode job) {
        return job.path("annual_from").asInt(0) > 0
                || job.path("annual_to").asInt(0) > 0;
    }

    private LocalDateTime parseDeadlineAt(String dueTime) {
        if (dueTime == null || dueTime.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(dueTime).atTime(23, 59);
        } catch (DateTimeParseException ignored) {
            // Try datetime formats below.
        }

        try {
            return OffsetDateTime.parse(dueTime).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // Try local datetime format below.
        }

        try {
            return LocalDateTime.parse(dueTime);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private void validateRequired(
            String fieldName,
            String value,
            FetchedJobPosting fetchedJobPosting
    ) {
        if (value == null || value.isBlank()) {
            throw new JobPostingParseException(
                    "Required field is missing. field="
                            + fieldName
                            + ", source="
                            + fetchedJobPosting.source()
                            + ", externalId="
                            + fetchedJobPosting.externalId()
            );
        }
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return restoreWantedWordBreaks(value)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String restoreWantedWordBreaks(String value) {
        return value
                .replaceAll("(?<=[A-Za-z])\\R(?=[a-z])", "")
                .replaceAll("(?<=[A-Za-z])\\R(?=[0-9])", "")
                .replaceAll("(?<=[0-9])\\R(?=[A-Za-z])", "");
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength);
    }

    private record ExperienceRange(
            Integer min,
            Integer max
    ) {
    }
}
