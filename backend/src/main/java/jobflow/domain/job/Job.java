package jobflow.domain.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import jobflow.domain.common.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "jobs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Job extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(length = 100)
    private String externalId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 255)
    private String companyName;

    @Lob
    @Column(nullable = false, columnDefinition = "longtext")
    private String description;

    @Column(length = 500)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private JobRole role;

    @Column(length = 100)
    private String roleDetail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CareerLevel careerLevel = CareerLevel.ANY;

    private Integer minExperienceYears;

    private Integer maxExperienceYears;

    @Column(length = 30)
    private String educationLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EmploymentType employmentType = EmploymentType.FULL_TIME;

    @Column(length = 30)
    private String companySize;

    @Column(length = 100)
    private String industry;

    @Column(nullable = false, length = 50)
    private String locationCountry = "KR";

    @Column(length = 100)
    private String locationRegion;

    @Column(length = 100)
    private String locationCity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RemoteType remoteType = RemoteType.ONSITE;

    private Integer salaryMin;

    private Integer salaryMax;

    @Column(nullable = false, length = 3)
    private String salaryCurrency = "KRW";

    @Column(nullable = false)
    private boolean salaryVisible;

    private Integer hiringCount;

    private LocalDateTime openedAt;

    private LocalDateTime deadlineAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private JobStatus status = JobStatus.OPEN;
}
