package jobflow.collector.job.ingest;

import java.time.LocalDateTime;
import jobflow.collector.job.CareerLevel;
import jobflow.collector.job.EmploymentType;
import jobflow.collector.job.RemoteType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

@Component
public class ZighangJobPostingParser implements JobPostingParser {

    private static final String CRAWLER_VERSION = "zighang-parser-0.1";
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
        String title = firstText(
                document,
                "[data-testid=job-title]",
                "h1",
                "meta[property=og:title]"
        );
        String companyName = firstText(
                document,
                "[data-testid=company-name]",
                ".company-name",
                "meta[property=og:site_name]"
        );
        String description = firstText(
                document,
                "[data-testid=job-description]",
                "main",
                "body"
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
                null,
                null,
                inferRemoteType(pageText),
                null,
                null,
                "KRW",
                false,
                null,
                null,
                null,
                now,
                now,
                null,
                rawData(fetchedJobPosting, title, companyName),
                CRAWLER_VERSION
        );
    }

    private String firstText(Document document, String... selectors) {
        for (String selector : selectors) {
            String value = text(document, selector);

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

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }

        return false;
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
