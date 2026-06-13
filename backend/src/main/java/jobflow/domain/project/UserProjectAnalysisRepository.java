package jobflow.domain.project;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProjectAnalysisRepository extends JpaRepository<UserProjectAnalysis, Long> {

    Optional<UserProjectAnalysis> findFirstByUserProjectIdAndUserProjectUserIdOrderByAnalyzedAtDescIdDesc(
            Long userProjectId,
            Long userId
    );

    Optional<UserProjectAnalysis>
            findFirstByUserProjectIdAndUserProjectUserIdAndModelVersionOrderByAnalyzedAtDescIdDesc(
                    Long userProjectId,
                    Long userId,
                    String modelVersion
            );

    @Query("""
            SELECT COALESCE(MAX(analysis.analysisVersion), 0)
            FROM UserProjectAnalysis analysis
            WHERE analysis.userProject.id = :userProjectId
            """)
    int findMaxAnalysisVersionByUserProjectId(@Param("userProjectId") Long userProjectId);
}
