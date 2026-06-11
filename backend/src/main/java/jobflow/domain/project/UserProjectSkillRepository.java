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
}
