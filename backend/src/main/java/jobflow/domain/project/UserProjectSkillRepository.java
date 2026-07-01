package jobflow.domain.project;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProjectSkillRepository extends JpaRepository<UserProjectSkill, Long> {

    @Query("""
            SELECT DISTINCT ups.skill.id
            FROM UserProjectSkill ups
            WHERE ups.analysis.id = :analysisId
            ORDER BY ups.skill.id
            """)
    List<Long> findDistinctSkillIdsByAnalysisId(@Param("analysisId") Long analysisId);

    @Query(value = """
            SELECT DISTINCT ups.skill_id
            FROM user_project_skills ups
            WHERE ups.analysis_id = (
                SELECT upa.id
                FROM user_project_analysis upa
                JOIN user_projects up ON up.id = upa.user_project_id
                WHERE upa.user_project_id = :userProjectId
                  AND up.user_id = :userId
                ORDER BY upa.analyzed_at DESC, upa.id DESC
                LIMIT 1
            )
            ORDER BY ups.skill_id
            """, nativeQuery = true)
    List<Long> findDistinctSkillIdsByLatestOwnedProjectAnalysis(
            @Param("userId") Long userId,
            @Param("userProjectId") Long userProjectId
    );

    @Query("""
            SELECT ups
            FROM UserProjectSkill ups
            JOIN FETCH ups.skill skill
            WHERE ups.analysis.id = :analysisId
            ORDER BY ups.confidence DESC, skill.name ASC, ups.id ASC
            """)
    List<UserProjectSkill> findByAnalysisIdWithSkill(@Param("analysisId") Long analysisId);
}
