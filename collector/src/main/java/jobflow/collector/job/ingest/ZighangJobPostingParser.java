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
import org.springframework.stereotype.Component;

@Component
public class ZighangJobPostingParser implements JobPostingParser {

    private static final String CRAWLER_VERSION = "zighang-parser-0.1";
    private static final Pattern KOREAN_DATE_PATTERN =
            Pattern.compile("(20\\d{2})\\.\\s*(\\d{1,2})\\.\\s*(\\d{1,2})\\.");
    private static final List<String> INVALID_TITLE_KEYWORDS = List.of(
            "직무 탐색",
            "채용 정보"
    );
    private static final List<String> INVALID_COMPANY_KEYWORDS = List.of(
            "기업 정보 보기",
            "회사 정보 보기"
    );

    private final JdJobRoleClassificationService jdJobRoleClassificationService;

    public ZighangJobPostingParser(JdJobRoleClassificationService jdJobRoleClassificationService) {
        this.jdJobRoleClassificationService = jdJobRoleClassificationService;
    }

    @Override
    public boolean supports(JobIngestionSource source) {
        return source == JobIngestionSource.ZIGHANG;
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
                "[data-testid=job-title]",
                "h1",
                "meta[property=og:title]"
        ));
        String companyName = firstValidCompanyText(
                document,
                "[data-testid=company-name]",
                "[data-testid=company]",
                "[class*=companyName]",
                "[class*=company-name]",
                ".company-name",
                ".company"
        );
        String description = firstValidText(
                document,
                "[data-testid=job-description]",
                "[class*=job-description]",
                "main"
        );

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
                inferCareerLevel(pageText),
                null,
                null,
                null,
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

    private String firstValidCompanyText(Document document, String... selectors) {
        for (String selector : selectors) {
            String value = text(document, selector);

            if (value != null && !value.isBlank() && !containsAny(value, INVALID_COMPANY_KEYWORDS.toArray(String[]::new))) {
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

    private CareerLevel inferCareerLevel(String pageText) {
        if (containsAny(pageText, "신입", "인턴", "newcomer")) {
            return CareerLevel.NEWCOMER;
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

        if (containsAny(pageText, "경기", "성남", "판교", "분당")) {
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

        if (containsAny(pageText, "판교", "분당")) {
            return "Bundang";
        }

        return null;
    }

    private LocalDateTime inferDeadlineAt(String pageText) {
        Matcher matcher = KOREAN_DATE_PATTERN.matcher(pageText);

        while (matcher.find()) {
            int end = Math.min(pageText.length(), matcher.end() + 12);
            String dateContext = pageText.substring(matcher.start(), end);

            if (!dateContext.contains("마감")) {
                continue;
            }

            LocalDate date = LocalDate.of(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3))
            );

            return date.atTime(23, 59);
        }

        return null;
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

    private String rawData(FetchedJobPosting fetchedJobPosting, String title, String companyName) {
        return """
                {"source":"%s","externalId":"%s","detailUrl":"%s","title":"%s","companyName":"%s"}
                """.formatted(
                fetchedJobPosting.source(),
                escapeJson(fetchedJobPosting.externalId()),
                escapeJson(fetchedJobPosting.detailUrl()),
                escapeJson(title),
                escapeJson(companyName)
        );
    }

    private String escapeJson(String value) {
        return value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value.replaceAll("\\s+", " ").trim();
    }
}
