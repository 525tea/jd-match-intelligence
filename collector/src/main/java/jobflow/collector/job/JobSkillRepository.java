package jobflow.collector.job;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JobSkillRepository extends JpaRepository<JobSkill, Long> {

    void deleteByJobId(Long jobId);
}
