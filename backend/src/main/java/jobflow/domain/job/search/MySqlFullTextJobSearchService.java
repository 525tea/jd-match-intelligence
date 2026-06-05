package jobflow.domain.job.search;

import java.util.List;
import jobflow.domain.job.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MySqlFullTextJobSearchService {

    private final JobRepository jobRepository;

    public List<JobSearchResult> search(String keyword, int limit) {
        String normalizedKeyword = keyword == null ? "" : keyword.strip();
        if (normalizedKeyword.isBlank()) {
            return List.of();
        }

        return jobRepository.searchOpenJobsByFullText(normalizedKeyword, normalizeLimit(limit))
                .stream()
                .map(JobSearchResult::fromProjection)
                .toList();
    }

    private int normalizeLimit(int limit) {
        return Math.clamp(limit, 1, 100);
    }
}
