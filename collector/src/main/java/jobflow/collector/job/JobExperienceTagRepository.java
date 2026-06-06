package jobflow.collector.job;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JobExperienceTagRepository extends JpaRepository<JobExperienceTag, Long> {

    void deleteByJobId(Long jobId);
}
