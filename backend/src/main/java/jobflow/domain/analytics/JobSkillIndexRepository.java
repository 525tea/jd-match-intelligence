package jobflow.domain.analytics;

import jobflow.domain.job.RequirementType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobSkillIndexRepository extends JpaRepository<JobSkillIndex, Long> {

    long countByRequirementType(RequirementType requirementType);
}
