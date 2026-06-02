package jobflow.domain.job;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobExperienceTagRepository extends JpaRepository<JobExperienceTag, Long> {

    List<JobExperienceTag> findByJobId(Long jobId);

    void deleteByJobId(Long jobId);
}
