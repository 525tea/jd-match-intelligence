package jobflow.domain.project;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProjectExperienceTagRepository extends JpaRepository<UserProjectExperienceTag, Long> {

    @Query("""
            SELECT upet
            FROM UserProjectExperienceTag upet
            JOIN FETCH upet.tagCode tagCode
            WHERE upet.analysis.id = :analysisId
            ORDER BY upet.confidence DESC, tagCode.code ASC, upet.id ASC
            """)
    List<UserProjectExperienceTag> findByAnalysisIdWithTagCode(@Param("analysisId") Long analysisId);
}
