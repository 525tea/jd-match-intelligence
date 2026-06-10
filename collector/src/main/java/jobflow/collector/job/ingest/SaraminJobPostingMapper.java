package jobflow.collector.job.ingest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
public class SaraminJobPostingMapper {

    private static final String CRAWLER_VERSION = "saramin-api-scaffold-0.1";
    private static final int ROLE_DETAIL_MAX_LENGTH = 100;
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter BASIC_OFFSET_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
    private static final Pattern EXPERIENCE_RANGE_PATTERN =
            Pattern.compile("(?:경력\\s*)?(\\d+)\\s*[~\\-–]\\s*(\\d+)\\s*년");
    private static final Pattern EXPERIENCE_MIN_PATTERN =
            Pattern.compile("(?:경력\\s*)?(\\d+)\\s*년\\s*이상");

    private final ObjectMapper objectMapper;
    private final JdJobRoleClassificationService jdJobRoleClassificationService;

    public IngestedJobPosting map(JsonNode job) {
        String externalId = firstText(job, "id", "job-id", "rec_idx");
        String title = normalize(firstText(job, "/position/title", "title", "position-title"));
        String companyName = normalize(firstText(job, "/company/detail/name", "/company/name", "company-name"));
        String originalUrl = normalize(firstText(job, "url", "detail-url", "source-url"));
        String roleDetail = truncate(normalize(String.join(" ", nonBlankTexts(
                firstText(job, "/position/job-code/name", "job-code-name"),
                firstText(job, "/position/job-mid-code/name", "job-mid-code-name"),
                firstText(job, "keyword", "keywords")
        ))), ROLE_DETAIL_MAX_LENGTH);
        String description = buildDescription(job, title, companyName, roleDetail);

        validateRequired("externalId", externalId);
        validateRequired("title", title);
        validateRequired("companyName", companyName);
        validateRequired("originalUrl", originalUrl);
        validateRequired("description", description);

        String pageText = normalize(title + " " + companyName + " " + roleDetail + " " + description);
        ExperienceRange experienceRange = inferExperienceRange(pageText);
        LocalDateTime now = LocalDateTime.now();

        return new IngestedJobPosting(
                JobIngestionSource.SARAMIN,
                externalId,
                title,
                companyName,
                description,
                originalUrl,
                originalUrl,
                classifyRole(title, roleDetail, description),
                roleDetail.isBlank() ? null : roleDetail,
                inferCareerLevel(pageText, experienceRange),
                experienceRange.min(),
                experienceRange.max(),
                normalize(firstText(job, "/position/required-education-level/name", "education-level")),
                inferEmploymentType(pageText),
                null,
                normalize(firstText(job, "/company/detail/industry", "/company/industry", "industry")),
                "KR",
                inferRegion(firstText(job, "/position/location/name", "location", "location-name")),
                inferCity(firstText(job, "/position/location/name", "location", "location-name")),
                inferRemoteType(pageText),
                null,
                null,
                "KRW",
                false,
                null,
                parseDateTime(firstText(job, "posting-date", "posting-timestamp", "opened-at")),
                parseDateTime(firstText(job, "expiration-date", "expiration-timestamp", "deadline-at")),
                now,
                now,
                parseDateTime(firstText(job, "modification-date", "updated-at")),
                writeRawData(job),
                CRAWLER_VERSION
        );
    }

    private JobRole classifyRole(String title, String roleDetail, String description) {
        JobRole titleRole = jdJobRoleClassificationService.classify(title);

        if (titleRole != JobRole.ETC) {
            return titleRole;
        }

        return jdJobRoleClassificationService.classify(roleDetail, description);
    }

    private String buildDescription(
            JsonNode job,
            String title,
            String companyName,
            String roleDetail
    ) {
        List<String> parts = new ArrayList<>();
        appendIfPresent(parts, title);
        appendIfPresent(parts, companyName);
        appendIfPresent(parts, roleDetail);
        appendIfPresent(parts, firstText(job, "/position/job-code/name", "job-code-name"));
        appendIfPresent(parts, firstText(job, "/position/job-mid-code/name", "job-mid-code-name"));
        appendIfPresent(parts, firstText(job, "/position/industry/name", "industry-name"));
        appendIfPresent(parts, firstText(job, "keyword", "keywords"));
        appendIfPresent(parts, firstText(job, "description", "job-description"));
        appendIfPresent(parts, firstText(job, "/position/experience-level/name", "experience-level"));
        appendIfPresent(parts, firstText(job, "/position/location/name", "location", "location-name"));

        return String.join("\n", parts);
    }

    private void appendIfPresent(List<String> parts, String value) {
        String normalized = normalize(value);

        if (!normalized.isBlank()) {
            parts.add(normalized);
        }
    }

    private String firstText(JsonNode node, String... paths) {
        for (String path : paths) {
            String value = text(node, path);

            if (!value.isBlank()) {
                return value;
            }
        }

        return "";
    }

    private String text(JsonNode node, String path) {
        JsonNode value = path.startsWith("/")
                ? node.at(path)
                : node.path(path);

        if (value.isMissingNode() || value.isNull()) {
            return "";
        }

        return value.asText("");
    }

    private List<String> nonBlankTexts(String... values) {
        List<String> result = new ArrayList<>();

        for (String value : values) {
            String normalized = normalize(value);

            if (!normalized.isBlank()) {
                result.add(normalized);
            }
        }

        return result;
    }

    private ExperienceRange inferExperienceRange(String pageText) {
        Matcher rangeMatcher = EXPERIENCE_RANGE_PATTERN.matcher(pageText);

        if (rangeMatcher.find()) {
            return new ExperienceRange(
                    Integer.parseInt(rangeMatcher.group(1)),
                    Integer.parseInt(rangeMatcher.group(2))
            );
        }

        Matcher minMatcher = EXPERIENCE_MIN_PATTERN.matcher(pageText);

        if (minMatcher.find()) {
            return new ExperienceRange(
                    Integer.parseInt(minMatcher.group(1)),
                    null
            );
        }

        if (containsAny(pageText, "신입", "경력 무관", "경력무관")) {
            return new ExperienceRange(0, null);
        }

        return new ExperienceRange(null, null);
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

    private RemoteType inferRemoteType(String pageText) {
        String lower = pageText.toLowerCase();

        if (containsAny(lower, "remote", "원격", "재택")) {
            return RemoteType.REMOTE;
        }

        if (containsAny(lower, "hybrid", "하이브리드")) {
            return RemoteType.HYBRID;
        }

        return RemoteType.ONSITE;
    }

    private String inferRegion(String location) {
        String value = normalize(location);

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

        return value.isBlank() ? null : value;
    }

    private String inferCity(String location) {
        String value = normalize(location);

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

    private LocalDateTime parseDateTime(String value) {
        String normalized = normalize(value);

        if (normalized.isBlank()) {
            return null;
        }

        if (normalized.matches("\\d{10,13}")) {
            long timestamp = Long.parseLong(normalized);

            if (normalized.length() == 13) {
                timestamp = timestamp / 1000;
            }

            return LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), SEOUL_ZONE);
        }

        try {
            return LocalDate.parse(normalized).atTime(23, 59);
        } catch (DateTimeParseException ignored) {
            // Try datetime formats below.
        }

        try {
            return OffsetDateTime.parse(normalized).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // Try Saramin offset datetime format below.
        }

        try {
            return OffsetDateTime.parse(normalized, BASIC_OFFSET_DATE_TIME).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // Try local datetime format below.
        }

        try {
            return LocalDateTime.parse(normalized);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private String writeRawData(JsonNode job) {
        try {
            return objectMapper.writeValueAsString(job);
        } catch (JacksonException exception) {
            throw new JobPostingParseException(
                    "Failed to serialize Saramin raw data. error=" + exception.getMessage()
            );
        }
    }

    private void validateRequired(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new JobPostingParseException(
                    "Required Saramin field is missing. field=" + fieldName
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

        return value.replaceAll("\\s+", " ").trim();
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
