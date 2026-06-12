package jobflow.domain.project;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProjectAnalysisRepository extends JpaRepository<UserProjectAnalysis, Long> {

    Optional<UserProjectAnalysis> findFirstByUserProjectIdAndUserProjectUserIdOrderByAnalyzedAtDescIdDesc(
            Long userProjectId,
            Long userId
    );
}
