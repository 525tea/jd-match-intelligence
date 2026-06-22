package jobflow.collector.job;

import jakarta.persistence.*;
import jobflow.collector.common.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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

    @Column(length = 128)
    private String canonicalFingerprint;

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

    @Column(length = 1000)
    private String originalUrl;

    private LocalDateTime collectedAt;

    private LocalDateTime lastSeenAt;

    private LocalDateTime sourceUpdatedAt;

    @Column(columnDefinition = "json")
    private String rawData;

    @Column(length = 500)
    private String rawSnapshotKey;

    @Column(length = 64)
    private String rawSnapshotHash;

    private Long rawSnapshotSizeBytes;

    @Column(length = 30)
    private String rawSnapshotStorageType;

    private LocalDateTime rawSnapshotSavedAt;

    @Column(length = 50)
    private String crawlerVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private JobStatus status = JobStatus.OPEN;

    public static Job create(
            String source,
            String externalId,
            String title,
            String companyName,
            String description,
            String url,
            JobRole role,
            String roleDetail,
            CareerLevel careerLevel,
            Integer minExperienceYears,
            Integer maxExperienceYears,
            String educationLevel,
            EmploymentType employmentType,
            String companySize,
            String industry,
            String locationCountry,
            String locationRegion,
            String locationCity,
            RemoteType remoteType,
            Integer salaryMin,
            Integer salaryMax,
            String salaryCurrency,
            boolean salaryVisible,
            Integer hiringCount,
            LocalDateTime openedAt,
            LocalDateTime deadlineAt
    ) {
        Job job = new Job();
        job.source = source;
        job.externalId = externalId;
        job.title = title;
        job.companyName = companyName;
        job.description = description;
        job.url = url;
        job.role = role;
        job.roleDetail = roleDetail;
        job.careerLevel = careerLevel;
        job.minExperienceYears = minExperienceYears;
        job.maxExperienceYears = maxExperienceYears;
        job.educationLevel = educationLevel;
        job.employmentType = employmentType;
        job.companySize = companySize;
        job.industry = industry;
        job.locationCountry = locationCountry;
        job.locationRegion = locationRegion;
        job.locationCity = locationCity;
        job.remoteType = remoteType;
        job.salaryMin = salaryMin;
        job.salaryMax = salaryMax;
        job.salaryCurrency = salaryCurrency;
        job.salaryVisible = salaryVisible;
        job.hiringCount = hiringCount;
        job.openedAt = openedAt;
        job.deadlineAt = deadlineAt;
        job.status = JobStatus.OPEN;
        return job;
    }

    public void update(
            String title,
            String companyName,
            String description,
            String url,
            JobRole role,
            String roleDetail,
            CareerLevel careerLevel,
            Integer minExperienceYears,
            Integer maxExperienceYears,
            String educationLevel,
            EmploymentType employmentType,
            String companySize,
            String industry,
            String locationCountry,
            String locationRegion,
            String locationCity,
            RemoteType remoteType,
            Integer salaryMin,
            Integer salaryMax,
            String salaryCurrency,
            boolean salaryVisible,
            Integer hiringCount,
            LocalDateTime openedAt,
            LocalDateTime deadlineAt
    ) {
        this.title = title;
        this.companyName = companyName;
        this.description = description;
        this.url = url;
        this.role = role;
        this.roleDetail = roleDetail;
        this.careerLevel = careerLevel;
        this.minExperienceYears = minExperienceYears;
        this.maxExperienceYears = maxExperienceYears;
        this.educationLevel = educationLevel;
        this.employmentType = employmentType;
        this.companySize = companySize;
        this.industry = industry;
        this.locationCountry = locationCountry;
        this.locationRegion = locationRegion;
        this.locationCity = locationCity;
        this.remoteType = remoteType;
        this.salaryMin = salaryMin;
        this.salaryMax = salaryMax;
        this.salaryCurrency = salaryCurrency;
        this.salaryVisible = salaryVisible;
        this.hiringCount = hiringCount;
        this.openedAt = openedAt;
        this.deadlineAt = deadlineAt;
    }

    public void updateRole(JobRole role) {
        this.role = role;
    }

    public void updateDescription(String description) {
        this.description = description;
    }

    public void close() {
        validateOpenStatus();
        this.status = JobStatus.CLOSED;
    }

    public void updateCrawlingMetadata(
            String canonicalFingerprint,
            String originalUrl,
            LocalDateTime collectedAt,
            LocalDateTime lastSeenAt,
            LocalDateTime sourceUpdatedAt,
            String rawData,
            String crawlerVersion
    ) {
        this.canonicalFingerprint = canonicalFingerprint;
        this.originalUrl = originalUrl;
        this.collectedAt = collectedAt;
        this.lastSeenAt = lastSeenAt;
        this.sourceUpdatedAt = sourceUpdatedAt;
        this.rawData = rawData;
        this.crawlerVersion = crawlerVersion;
    }

    public void updateRawSnapshotMetadata(
            String rawSnapshotKey,
            String rawSnapshotHash,
            Long rawSnapshotSizeBytes,
            String rawSnapshotStorageType,
            LocalDateTime rawSnapshotSavedAt
    ) {
        this.rawSnapshotKey = rawSnapshotKey;
        this.rawSnapshotHash = rawSnapshotHash;
        this.rawSnapshotSizeBytes = rawSnapshotSizeBytes;
        this.rawSnapshotStorageType = rawSnapshotStorageType;
        this.rawSnapshotSavedAt = rawSnapshotSavedAt;
    }

    public void clearRawData() {
        this.rawData = null;
    }

    public void expire() {
        validateOpenStatus();
        this.status = JobStatus.EXPIRED;
    }

    private void validateOpenStatus() {
        if (status != JobStatus.OPEN) {
            throw new IllegalStateException("Job status must be OPEN. status=" + status);
        }
    }
}
