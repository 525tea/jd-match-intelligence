package jobflow.collector.normalization;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NormalizationCandidateRepository extends JpaRepository<NormalizationCandidate, Long> {

    Optional<NormalizationCandidate> findByTypeAndSourceAndNormalizedValue(
            NormalizationCandidateType type,
            String source,
            String normalizedValue
    );
}
