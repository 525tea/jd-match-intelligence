package jobflow.domain.job;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobSkillRepository extends JpaRepository<JobSkill, Long> {

    List<JobSkill> findByJobId(Long jobId);

    void deleteByJobId(Long jobId);
}
