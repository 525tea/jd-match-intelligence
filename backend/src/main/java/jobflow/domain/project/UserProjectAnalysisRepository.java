package jobflow.domain.project;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProjectAnalysisRepository extends JpaRepository<UserProjectAnalysis, Long> {

    Optional<UserProjectAnalysis> findFirstByOrderByAnalyzedAtDescIdDesc();

    @Query("""
            SELECT DISTINCT analysis
            FROM UserProjectAnalysis analysis
            JOIN UserProjectSkill projectSkill ON projectSkill.analysis = analysis
            ORDER BY analysis.analyzedAt DESC, analysis.id DESC
            """)
    List<UserProjectAnalysis> findWithProjectSkillsOrderByAnalyzedAtDescIdDesc(Pageable pageable);

    @Query("""
            SELECT DISTINCT analysis
            FROM UserProjectAnalysis analysis
            JOIN UserProjectSkill projectSkill ON projectSkill.analysis = analysis
            WHERE analysis.userProject.user.id = :userId
            ORDER BY analysis.analyzedAt DESC, analysis.id DESC
            """)
    List<UserProjectAnalysis> findWithProjectSkillsByUserIdOrderByAnalyzedAtDescIdDesc(
            @Param("userId") Long userId,
            Pageable pageable
    );

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
