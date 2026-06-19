package jobflow.collector.job.ingest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jobflow.collector.job.CareerLevel;
import jobflow.collector.job.EmploymentType;
import jobflow.collector.job.RemoteType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

@Component
public class JumpitJobPostingParser implements JobPostingParser {

    private static final String CRAWLER_VERSION = "jumpit-parser-0.1";
    private static final Pattern KOREAN_DATE_PATTERN =
            Pattern.compile("(20\\d{2})\\.\\s*(\\d{1,2})\\.\\s*(\\d{1,2})\\.");
    private static final Pattern ISO_DATE_PATTERN =
            Pattern.compile("(20\\d{2})-(\\d{1,2})-(\\d{1,2})");
    private static final Pattern CAREER_RANGE_PATTERN =
            Pattern.compile("경력\\s*(\\d+)\\s*[~\\-–]\\s*(\\d+)년");
    private static final Pattern CAREER_MIN_PATTERN =
            Pattern.compile("경력\\s*(\\d+)년\\s*이상");
    private static final List<String> INVALID_TITLE_KEYWORDS = List.of(
            "점핏",
            "점핏 | 개발 직무 탐색",
            "개발 직무 탐색"
    );
    private static final List<String> INVALID_COMPANY_KEYWORDS = List.of(
            "기업 정보 보기",
            "회사 정보 보기",
            "점핏"
    );

    private final JdJobRoleClassificationService jdJobRoleClassificationService;

    public JumpitJobPostingParser(JdJobRoleClassificationService jdJobRoleClassificationService) {
        this.jdJobRoleClassificationService = jdJobRoleClassificationService;
    }

    @Override
    public boolean supports(JobIngestionSource source) {
        return source == JobIngestionSource.JUMPIT;
    }

    @Override
    public IngestedJobPosting parse(FetchedJobPosting fetchedJobPosting) {
        if (!supports(fetchedJobPosting.source())) {
            throw new JobPostingParseException(
                    "Unsupported source. source=" + fetchedJobPosting.source()
            );
        }

        Document document = Jsoup.parse(fetchedJobPosting.body(), fetchedJobPosting.detailUrl());
        String pageText = normalize(document.text());
        String title = cleanTitle(firstValidText(
                document,
                "[data-testid=position-title]",
                "[data-testid=job-title]",
                ".position-title",
                "h1",
                "meta[property=og:title]"
        ));
        String companyName = firstNonBlank(
                firstValidCompanyText(
                        document,
                        "a[href^=/company/]",
                        "a[href*=jumpit.saramin.co.kr/company/]",
                        "a[href*=company]",
                        "[data-testid=company-name]",
                        "[data-testid=company]",
                        "[class*=companyName]",
                        "[class*=company-name]",
                        ".company-name",
                        ".company"
                ),
                inferCompanyName(pageText, title)
        );
        String description = firstValidMultilineText(
                document,
                "[data-testid=position-description]",
                "[data-testid=job-description]",
                "[class*=position-description]",
                "[class*=job-description]",
                ".position-description",
                "main"
        );
        ExperienceRange experienceRange = inferExperienceRange(pageText);

        validateRequired("title", title, fetchedJobPosting);
        validateRequired("companyName", companyName, fetchedJobPosting);
        validateRequired("description", description, fetchedJobPosting);

        LocalDateTime now = LocalDateTime.now();

        return new IngestedJobPosting(
                fetchedJobPosting.source(),
                fetchedJobPosting.externalId(),
                title,
                companyName,
                description,
                fetchedJobPosting.sourceUrl(),
                fetchedJobPosting.detailUrl(),
                jdJobRoleClassificationService.classify(title, description, pageText),
                null,
                inferCareerLevel(pageText, experienceRange),
                experienceRange.min(),
                experienceRange.max(),
                inferEducationLevel(pageText),
                inferEmploymentType(pageText),
                null,
                null,
                "KR",
                inferRegion(pageText),
                inferCity(pageText),
                inferRemoteType(pageText),
                null,
                null,
                "KRW",
                false,
                null,
                null,
                inferDeadlineAt(pageText),
                now,
                now,
                null,
                rawData(fetchedJobPosting, title, companyName),
                CRAWLER_VERSION
        );
    }

    private String firstValidText(Document document, String... selectors) {
        for (String selector : selectors) {
            String value = text(document, selector);

            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return "";
    }

    private String firstValidMultilineText(Document document, String... selectors) {
        for (String selector : selectors) {
            String value = multilineText(document, selector);

            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return "";
    }

    private String firstValidCompanyText(Document document, String... selectors) {
        for (String selector : selectors) {
            String value = cleanCompanyName(text(document, selector));

            if (!value.isBlank()) {
                return value;
            }
        }

        return "";
    }

    private String text(Document document, String selector) {
        if (selector.startsWith("meta")) {
            return normalize(document.select(selector).attr("content"));
        }

        return normalize(document.select(selector).first() == null
                ? ""
                : document.select(selector).first().text());
    }

    private String multilineText(Document document, String selector) {
        Element element = document.select(selector).first();

        if (element == null) {
            return "";
        }

        String value = element.text();

        if (value == null || value.isBlank()) {
            value = element.wholeText();
        }

        return normalizeDescription(value);
    }

    private String normalizeDescription(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u00A0', ' ')
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n[ \\t]+", "\n")
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();

        normalized = normalized.replaceAll(
                "\\s*(포지션 상세 정보|기술스택|주요업무|자격요건|우대사항|복지 및 혜택|채용절차 및 기타 지원 유의사항|포지션 경력/학력/마감일/근무지역 정보|기업/서비스 소개|팀 소개)\\s*",
                "\n\n$1\n"
        );
        normalized = normalized.replaceAll("\\s+([•ㆍ])\\s*", "\n$1 ");
        normalized = normalized.replaceAll("\\s+(\\[[^\\]\\n]{2,40}])\\s*", "\n$1\n");
        normalized = removeJumpitFooterNoise(normalized);
        normalized = normalized.replaceAll("\\n{3,}", "\n\n");

        return normalized.trim();
    }

    private String removeJumpitFooterNoise(String value) {
        String cleaned = value;

        cleaned = cleaned.replaceAll("기업상세 정보로 이동\\s*\\d+\\s*/\\s*\\d+\\s*", "");
        cleaned = cleaned.replaceAll("지도보기\\s*·?\\s*주소복사", "");
        cleaned = cleaned.replaceAll("홈페이지 바로가기\\s*", "");
        cleaned = cleaned.replaceAll("지원하기\\s+지원하기\\s+스크랩\\s+공유.*$", "");
        cleaned = cleaned.replaceAll("최종 합격하면 취업축하금\\s*50만원.*$", "");

        return cleaned.trim();
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

    private CareerLevel inferCareerLevel(String pageText, ExperienceRange experienceRange) {
        if (containsAny(pageText, "신입", "인턴", "newcomer")) {
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

        if (containsAny(pageText, "주니어", "1년", "2년", "3년", "junior")) {
            return CareerLevel.JUNIOR;
        }

        if (containsAny(pageText, "시니어", "senior", "리드", "lead")) {
            return CareerLevel.SENIOR;
        }

        if (containsAny(pageText, "4년", "5년", "6년", "7년", "mid")) {
            return CareerLevel.MID;
        }

        return CareerLevel.ANY;
    }

    private ExperienceRange inferExperienceRange(String pageText) {
        Matcher rangeMatcher = CAREER_RANGE_PATTERN.matcher(pageText);

        if (rangeMatcher.find()) {
            return new ExperienceRange(
                    Integer.parseInt(rangeMatcher.group(1)),
                    Integer.parseInt(rangeMatcher.group(2))
            );
        }

        Matcher minMatcher = CAREER_MIN_PATTERN.matcher(pageText);

        if (minMatcher.find()) {
            return new ExperienceRange(
                    Integer.parseInt(minMatcher.group(1)),
                    null
            );
        }

        if (containsAny(pageText, "경력 무관", "신입 가능")) {
            return new ExperienceRange(0, null);
        }

        return new ExperienceRange(null, null);
    }

    private String inferEducationLevel(String pageText) {
        if (containsAny(pageText, "대학교졸업(4년)", "4년제")) {
            return "BACHELOR";
        }

        if (containsAny(pageText, "학력 무관")) {
            return "ANY";
        }

        return null;
    }

    private EmploymentType inferEmploymentType(String pageText) {
        if (containsAny(pageText, "인턴", "intern")) {
            return EmploymentType.INTERN;
        }

        if (containsAny(pageText, "계약직", "contract")) {
            return EmploymentType.CONTRACT;
        }

        if (containsAny(pageText, "프리랜서", "freelance")) {
            return EmploymentType.FREELANCE;
        }

        return EmploymentType.FULL_TIME;
    }

    private RemoteType inferRemoteType(String pageText) {
        if (containsAny(pageText, "원격", "재택", "remote")) {
            return RemoteType.REMOTE;
        }

        if (containsAny(pageText, "하이브리드", "hybrid")) {
            return RemoteType.HYBRID;
        }

        return RemoteType.ONSITE;
    }

    private String inferRegion(String pageText) {
        if (containsAny(pageText, "서울", "seoul")) {
            return "Seoul";
        }

        if (containsAny(pageText, "경기", "성남", "판교", "분당", "과천")) {
            return "Gyeonggi";
        }

        if (containsAny(pageText, "부산", "busan")) {
            return "Busan";
        }

        return null;
    }

    private String inferCity(String pageText) {
        if (containsAny(pageText, "강남")) {
            return "Gangnam";
        }

        if (containsAny(pageText, "서초")) {
            return "Seocho";
        }

        if (containsAny(pageText, "성동")) {
            return "Seongdong";
        }

        if (containsAny(pageText, "과천")) {
            return "Gwacheon";
        }

        if (containsAny(pageText, "판교", "분당")) {
            return "Bundang";
        }

        return null;
    }

    private LocalDateTime inferDeadlineAt(String pageText) {
        Matcher isoMatcher = ISO_DATE_PATTERN.matcher(pageText);

        while (isoMatcher.find()) {
            int start = Math.max(0, isoMatcher.start() - 12);
            int end = Math.min(pageText.length(), isoMatcher.end() + 12);
            String dateContext = pageText.substring(start, end);

            if (!dateContext.contains("마감")) {
                continue;
            }

            return LocalDate.of(
                    Integer.parseInt(isoMatcher.group(1)),
                    Integer.parseInt(isoMatcher.group(2)),
                    Integer.parseInt(isoMatcher.group(3))
            ).atTime(23, 59);
        }

        Matcher koreanMatcher = KOREAN_DATE_PATTERN.matcher(pageText);

        while (koreanMatcher.find()) {
            int end = Math.min(pageText.length(), koreanMatcher.end() + 12);
            String dateContext = pageText.substring(koreanMatcher.start(), end);

            if (!dateContext.contains("마감")) {
                continue;
            }

            return LocalDate.of(
                    Integer.parseInt(koreanMatcher.group(1)),
                    Integer.parseInt(koreanMatcher.group(2)),
                    Integer.parseInt(koreanMatcher.group(3))
            ).atTime(23, 59);
        }

        return null;
    }

    private String inferCompanyName(String pageText, String title) {
        if (title == null || title.isBlank()) {
            return "";
        }

        Pattern pattern = Pattern.compile(Pattern.quote(title) + "\\s+(.{1,50}?)\\s+취업축하금");
        Matcher matcher = pattern.matcher(pageText);

        if (matcher.find()) {
            return cleanCompanyName(matcher.group(1));
        }

        return "";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    private String cleanTitle(String value) {
        String title = normalize(value);

        if (title.contains("|")) {
            title = normalize(title.split("\\|", 2)[0]);
        }

        if (containsAny(title, INVALID_TITLE_KEYWORDS.toArray(String[]::new))) {
            return "";
        }

        return title;
    }

    private String cleanCompanyName(String value) {
        String companyName = normalize(value);

        if (companyName.contains("|")) {
            companyName = normalize(companyName.split("\\|", 2)[0]);
        }

        if (containsAny(companyName, INVALID_COMPANY_KEYWORDS.toArray(String[]::new))) {
            return "";
        }

        return companyName;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return "";
    }

    private String rawData(FetchedJobPosting fetchedJobPosting, String title, String companyName) {
        return """
                {"source":"%s","externalId":"%s","detailUrl":"%s","title":"%s","companyName":"%s","rawBody":"%s"}
                """.formatted(
                fetchedJobPosting.source(),
                escapeJson(fetchedJobPosting.externalId()),
                escapeJson(fetchedJobPosting.detailUrl()),
                escapeJson(title),
                escapeJson(companyName),
                escapeJson(fetchedJobPosting.body())
        );
    }

    private String escapeJson(String value) {
        return value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value.replaceAll("\\s+", " ").trim();
    }

    private record ExperienceRange(
            Integer min,
            Integer max
    ) {
    }
}
