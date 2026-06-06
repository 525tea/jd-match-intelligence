package jobflow.collector.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jobflow.collector.skill.ExperienceTagCode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "job_experience_tags")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobExperienceTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tag_code", nullable = false)
    private ExperienceTagCode tagCode;

    @Column(length = 500)
    private String sourcePhrase;

    public static JobExperienceTag create(
            Job job,
            ExperienceTagCode tagCode,
            String sourcePhrase
    ) {
        JobExperienceTag jobExperienceTag = new JobExperienceTag();
        jobExperienceTag.job = job;
        jobExperienceTag.tagCode = tagCode;
        jobExperienceTag.sourcePhrase = sourcePhrase;
        return jobExperienceTag;
    }
}
