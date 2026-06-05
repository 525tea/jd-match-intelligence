package jobflow.domain.job;

import java.time.LocalDateTime;

public interface JobSearchProjection {

    Long getId();

    String getTitle();

    String getCompanyName();

    String getRole();

    String getCareerLevel();

    String getEmploymentType();

    String getLocationRegion();

    String getLocationCity();

    String getRemoteType();

    LocalDateTime getDeadlineAt();

    String getStatus();

    Double getScore();
}
