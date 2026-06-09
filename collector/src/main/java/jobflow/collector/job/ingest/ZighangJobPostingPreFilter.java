package jobflow.collector.job.ingest;

import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

@Component
public class ZighangJobPostingPreFilter implements JobPostingPreFilter {

    private static final List<String> TARGET_CATEGORY_KEYWORDS = List.of(
            "IT",
            "개발",
            "데이터",
            "소프트웨어",
            "인프라",
            "보안",
            "QA",
            "DevOps",
            "SRE",
            "AI",
            "머신러닝",
            "백엔드",
            "프론트엔드",
            "서버",
            "모바일",
            "iOS",
            "Android"
    );

    @Override
    public boolean supports(JobIngestionSource source) {
        return source == JobIngestionSource.ZIGHANG;
    }

    @Override
    public boolean shouldSkip(FetchedJobPosting fetchedJobPosting) {
        Document document = Jsoup.parse(fetchedJobPosting.body(), fetchedJobPosting.detailUrl());
        String sourceCategory = firstNonBlank(
                extractCategory(document.select("meta[property=og:title]").attr("content")),
                extractCategory(document.title())
        );

        if (sourceCategory.isBlank()) {
            return true;
        }

        return TARGET_CATEGORY_KEYWORDS.stream()
                .noneMatch(sourceCategory::contains);
    }

    private String extractCategory(String titleLikeValue) {
        String value = normalize(titleLikeValue);

        if (value.isBlank() || !value.contains("|")) {
            return "";
        }

        String[] parts = value.split("\\|");

        if (parts.length < 2) {
            return "";
        }

        return normalize(parts[parts.length - 1]);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return "";
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value.replaceAll("\\s+", " ").trim();
    }
}
