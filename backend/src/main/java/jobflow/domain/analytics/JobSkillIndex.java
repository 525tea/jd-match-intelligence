package jobflow.domain.analytics;

import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.Job;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.JobStatus;
import jobflow.domain.job.RemoteType;
import jobflow.domain.job.RequirementType;
import jobflow.domain.skill.Skill;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "job_skill_index",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_job_skill_index_job_skill_requirement",
                        columnNames = {"job_id", "skill_id", "requirement_type"}
                )
        },
        indexes = {
                @Index(name = "idx_job_skill_index_skill_requirement", columnList = "skill_id, requirement_type"),
                @Index(name = "idx_job_skill_index_job", columnList = "job_id"),
                @Index(name = "idx_job_skill_index_role_career", columnList = "role, career_level"),
                @Index(name = "idx_job_skill_index_location", columnList = "location_region, location_city"),
                @Index(name = "idx_job_skill_index_deadline", columnList = "deadline_at")
        }
)
public class JobSkillIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Enumerated(EnumType.STRING)
    @Column(name = "requirement_type", nullable = false, length = 30)
    private RequirementType requirementType;

    @Column(name = "source", nullable = false, length = 50)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_status", nullable = false, length = 30)
    private JobStatus jobStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 50)
    private JobRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "career_level", nullable = false, length = 30)
    private CareerLevel careerLevel;

    @Column(name = "location_region", length = 100)
    private String locationRegion;

    @Column(name = "location_city", length = 100)
    private String locationCity;

    @Enumerated(EnumType.STRING)
    @Column(name = "remote_type", nullable = false, length = 30)
    private RemoteType remoteType;

    @Column(name = "deadline_at")
    private LocalDateTime deadlineAt;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;

    protected JobSkillIndex() {
    }

    private JobSkillIndex(Job job, Skill skill, RequirementType requirementType) {
        this.job = job;
        this.skill = skill;
        this.requirementType = requirementType;
        this.source = job.getSource();
        this.jobStatus = job.getStatus();
        this.role = job.getRole();
        this.careerLevel = job.getCareerLevel();
        this.locationRegion = job.getLocationRegion();
        this.locationCity = job.getLocationCity();
        this.remoteType = job.getRemoteType();
        this.deadlineAt = job.getDeadlineAt();
    }

    public static JobSkillIndex create(Job job, Skill skill, RequirementType requirementType) {
        return new JobSkillIndex(job, skill, requirementType);
    }

    @PrePersist
    void prePersist() {
        if (computedAt == null) {
            computedAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Job getJob() {
        return job;
    }

    public Skill getSkill() {
        return skill;
    }

    public RequirementType getRequirementType() {
        return requirementType;
    }

    public String getSource() {
        return source;
    }

    public JobStatus getJobStatus() {
        return jobStatus;
    }

    public JobRole getRole() {
        return role;
    }

    public CareerLevel getCareerLevel() {
        return careerLevel;
    }

    public String getLocationRegion() {
        return locationRegion;
    }

    public String getLocationCity() {
        return locationCity;
    }

    public RemoteType getRemoteType() {
        return remoteType;
    }

    public LocalDateTime getDeadlineAt() {
        return deadlineAt;
    }

    public LocalDateTime getComputedAt() {
        return computedAt;
    }
}
