package jobflow.domain.job.search;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JobSearchService {

    private final ElasticsearchJobSearchService elasticsearchJobSearchService;
    private final MySqlFullTextJobSearchService mySqlFullTextJobSearchService;

    public List<JobSearchResult> search(String keyword, int limit) {
        String normalizedKeyword = keyword == null ? "" : keyword.strip();
        if (normalizedKeyword.isBlank()) {
            return List.of();
        }

        try {
            return elasticsearchJobSearchService.search(normalizedKeyword, limit);
        } catch (RuntimeException exception) {
            return mySqlFullTextJobSearchService.search(normalizedKeyword, limit);
        }
    }
}
