package jobflow.domain.job;

import java.util.List;
import jobflow.domain.job.dto.JobCreateRequest;
import jobflow.domain.job.dto.JobExperienceTagRequest;
import jobflow.domain.job.dto.JobResponse;
import jobflow.domain.job.dto.JobSkillRequest;
import jobflow.domain.job.dto.JobSummaryResponse;
import jobflow.domain.job.dto.JobUpdateRequest;
import jobflow.domain.skill.ExperienceTagCode;
import jobflow.domain.skill.ExperienceTagCodeRepository;
import jobflow.domain.skill.Skill;
import jobflow.domain.skill.SkillRepository;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobService {

    private final JobRepository jobRepository;
    private final JobSkillRepository jobSkillRepository;
    private final JobExperienceTagRepository jobExperienceTagRepository;
    private final SkillRepository skillRepository;
    private final ExperienceTagCodeRepository experienceTagCodeRepository;

    @Transactional
    public JobResponse createJob(JobCreateRequest request) {
        Job job = Job.create(
                request.source(),
                request.externalId(),
                request.title(),
                request.companyName(),
                request.description(),
                request.url(),
                request.role(),
                request.roleDetail(),
                request.careerLevel(),
                request.minExperienceYears(),
                request.maxExperienceYears(),
                request.educationLevel(),
                request.employmentType(),
                request.companySize(),
                request.industry(),
                request.locationCountry(),
                request.locationRegion(),
                request.locationCity(),
                request.remoteType(),
                request.salaryMin(),
                request.salaryMax(),
                request.salaryCurrency(),
                request.salaryVisible(),
                request.hiringCount(),
                request.openedAt(),
                request.deadlineAt()
        );

        Job savedJob = jobRepository.save(job);
        List<JobSkill> jobSkills = saveJobSkills(savedJob, request.skills());
        List<JobExperienceTag> jobExperienceTags = saveJobExperienceTags(savedJob, request.experienceTags());

        return JobResponse.of(savedJob, jobSkills, jobExperienceTags);
    }

    public JobResponse getJob(Long jobId) {
        Job job = findJob(jobId);
        List<JobSkill> jobSkills = jobSkillRepository.findByJobId(jobId);
        List<JobExperienceTag> jobExperienceTags = jobExperienceTagRepository.findByJobId(jobId);

        return JobResponse.of(job, jobSkills, jobExperienceTags);
    }

    public List<JobSummaryResponse> getJobs() {
        return jobRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(JobSummaryResponse::from)
                .toList();
    }

    @Transactional
    public JobResponse updateJob(Long jobId, JobUpdateRequest request) {
        Job job = findJob(jobId);

        job.update(
                request.title(),
                request.companyName(),
                request.description(),
                request.url(),
                request.role(),
                request.roleDetail(),
                request.careerLevel(),
                request.minExperienceYears(),
                request.maxExperienceYears(),
                request.educationLevel(),
                request.employmentType(),
                request.companySize(),
                request.industry(),
                request.locationCountry(),
                request.locationRegion(),
                request.locationCity(),
                request.remoteType(),
                request.salaryMin(),
                request.salaryMax(),
                request.salaryCurrency(),
                request.salaryVisible(),
                request.hiringCount(),
                request.openedAt(),
                request.deadlineAt()
        );

        List<JobSkill> jobSkills = jobSkillRepository.findByJobId(jobId);
        List<JobExperienceTag> jobExperienceTags = jobExperienceTagRepository.findByJobId(jobId);

        return JobResponse.of(job, jobSkills, jobExperienceTags);
    }

    @Transactional
    public JobResponse closeJob(Long jobId) {
        Job job = findJob(jobId);
        job.close();

        List<JobSkill> jobSkills = jobSkillRepository.findByJobId(jobId);
        List<JobExperienceTag> jobExperienceTags = jobExperienceTagRepository.findByJobId(jobId);

        return JobResponse.of(job, jobSkills, jobExperienceTags);
    }

    @Transactional
    public JobResponse expireJob(Long jobId) {
        Job job = findJob(jobId);
        job.expire();

        List<JobSkill> jobSkills = jobSkillRepository.findByJobId(jobId);
        List<JobExperienceTag> jobExperienceTags = jobExperienceTagRepository.findByJobId(jobId);

        return JobResponse.of(job, jobSkills, jobExperienceTags);
    }

    private Job findJob(Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.JOB_NOT_FOUND));
    }

    private List<JobSkill> saveJobSkills(Job job, List<JobSkillRequest> skillRequests) {
        if (skillRequests == null || skillRequests.isEmpty()) {
            return List.of();
        }

        List<JobSkill> jobSkills = skillRequests.stream()
                .map(skillRequest -> {
                    Skill skill = skillRepository.findById(skillRequest.skillId())
                            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.SKILL_NOT_FOUND));

                    return JobSkill.create(
                            job,
                            skill,
                            skillRequest.requirementType()
                    );
                })
                .toList();

        return jobSkillRepository.saveAll(jobSkills);
    }

    private List<JobExperienceTag> saveJobExperienceTags(
            Job job,
            List<JobExperienceTagRequest> tagRequests
    ) {
        if (tagRequests == null || tagRequests.isEmpty()) {
            return List.of();
        }

        List<JobExperienceTag> jobExperienceTags = tagRequests.stream()
                .map(tagRequest -> {
                    ExperienceTagCode tagCode = experienceTagCodeRepository.findById(tagRequest.tagCode())
                            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.EXPERIENCE_TAG_NOT_FOUND));

                    return JobExperienceTag.create(
                            job,
                            tagCode,
                            tagRequest.sourcePhrase()
                    );
                })
                .toList();

        return jobExperienceTagRepository.saveAll(jobExperienceTags);
    }
}
