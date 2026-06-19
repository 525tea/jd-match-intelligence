package jobflow.domain.job;

import jobflow.domain.job.dto.JobCreateRequest;
import jobflow.domain.job.dto.JobExperienceTagRequest;
import jobflow.domain.job.dto.JobResponse;
import jobflow.domain.job.dto.JobSearchResponse;
import jobflow.domain.job.dto.JobSkillRequest;
import jobflow.domain.job.dto.JobSummaryResponse;
import jobflow.domain.job.dto.JobUpdateRequest;
import jobflow.domain.job.search.JobSearchService;
import jobflow.domain.outbox.OutboxEvent;
import jobflow.domain.outbox.OutboxEventService;
import jobflow.domain.outbox.OutboxEventTypes;
import jobflow.domain.outbox.payload.JobOutboxPayload;
import jobflow.domain.skill.ExperienceTagCode;
import jobflow.domain.skill.ExperienceTagCodeRepository;
import jobflow.domain.skill.Skill;
import jobflow.domain.skill.SkillRepository;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobService {

    private final JobRepository jobRepository;
    private final JobSkillRepository jobSkillRepository;
    private final JobExperienceTagRepository jobExperienceTagRepository;
    private final SkillRepository skillRepository;
    private final ExperienceTagCodeRepository experienceTagCodeRepository;
    private final OutboxEventService outboxEventService;
    private final JobSearchService jobSearchService;
    private final JobSkillNormalizationService jobSkillNormalizationService;
    private final JobExperienceTagNormalizationService jobExperienceTagNormalizationService;
    private final JdJobRoleClassificationService jdJobRoleClassificationService;
    private final JobApplyUrlResolver jobApplyUrlResolver;

    @Transactional
    public JobResponse createJob(JobCreateRequest request) {
        JobRole resolvedRole = jdJobRoleClassificationService.resolve(
                request.role(),
                request.title(),
                request.description(),
                request.roleDetail()
        );

        Job job = Job.create(
                request.source(),
                request.externalId(),
                request.title(),
                request.companyName(),
                request.description(),
                request.url(),
                resolvedRole,
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
        List<JobSkill> jobSkills = saveJobSkills(savedJob, request);
        List<JobExperienceTag> jobExperienceTags = saveJobExperienceTags(savedJob, request);

        outboxEventService.save(
                "JOB",
                savedJob.getId(),
                OutboxEventTypes.JOB_CREATED,
                JobOutboxPayload.from(savedJob),
                OutboxEvent.TOPIC_JOB_EVENTS
        );

        return JobResponse.of(savedJob, jobSkills, jobExperienceTags, jobApplyUrlResolver.resolve(savedJob));
    }

    public JobResponse getJob(Long jobId) {
        Job job = findJob(jobId);
        List<JobSkill> jobSkills = jobSkillRepository.findByJobId(jobId);
        List<JobExperienceTag> jobExperienceTags = jobExperienceTagRepository.findByJobId(jobId);

        return JobResponse.of(job, jobSkills, jobExperienceTags, jobApplyUrlResolver.resolve(job));
    }

    public List<JobSummaryResponse> getJobs() {
        return jobRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(job -> JobSummaryResponse.from(job, jobApplyUrlResolver.resolve(job)))
                .toList();
    }

    public List<JobSearchResponse> searchJobs(String keyword, int limit) {
        return jobSearchService.search(keyword, limit)
                .stream()
                .map(searchResult -> searchResult.toResponse(jobApplyUrlResolver.resolve(
                        searchResult.source(),
                        searchResult.externalId(),
                        null,
                        null
                )))
                .toList();
    }

    @Transactional
    public JobResponse updateJob(Long jobId, JobUpdateRequest request) {
        Job job = findJob(jobId);
        JobRole resolvedRole = jdJobRoleClassificationService.resolve(
                request.role(),
                request.title(),
                request.description(),
                request.roleDetail()
        );

        job.update(
                request.title(),
                request.companyName(),
                request.description(),
                request.url(),
                resolvedRole,
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

        outboxEventService.save(
                "JOB",
                job.getId(),
                OutboxEventTypes.JOB_UPDATED,
                JobOutboxPayload.from(job),
                OutboxEvent.TOPIC_JOB_EVENTS
        );

        List<JobSkill> jobSkills = jobSkillRepository.findByJobId(jobId);
        List<JobExperienceTag> jobExperienceTags = jobExperienceTagRepository.findByJobId(jobId);

        return JobResponse.of(job, jobSkills, jobExperienceTags, jobApplyUrlResolver.resolve(job));
    }

    @Transactional
    public JobResponse closeJob(Long jobId) {
        Job job = findJob(jobId);
        job.close();

        outboxEventService.save(
                "JOB",
                job.getId(),
                OutboxEventTypes.JOB_CLOSED,
                JobOutboxPayload.from(job),
                OutboxEvent.TOPIC_JOB_EVENTS
        );

        List<JobSkill> jobSkills = jobSkillRepository.findByJobId(jobId);
        List<JobExperienceTag> jobExperienceTags = jobExperienceTagRepository.findByJobId(jobId);

        return JobResponse.of(job, jobSkills, jobExperienceTags, jobApplyUrlResolver.resolve(job));
    }

    @Transactional
    public JobResponse expireJob(Long jobId) {
        Job job = findJob(jobId);
        job.expire();

        outboxEventService.save(
                "JOB",
                job.getId(),
                OutboxEventTypes.JOB_EXPIRED,
                JobOutboxPayload.from(job),
                OutboxEvent.TOPIC_JOB_EVENTS
        );

        List<JobSkill> jobSkills = jobSkillRepository.findByJobId(jobId);
        List<JobExperienceTag> jobExperienceTags = jobExperienceTagRepository.findByJobId(jobId);

        return JobResponse.of(job, jobSkills, jobExperienceTags, jobApplyUrlResolver.resolve(job));
    }

    private Job findJob(Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.JOB_NOT_FOUND));
    }

    private List<JobSkill> saveJobSkills(Job job, JobCreateRequest request) {
        List<JobSkillRequest> skillRequests = request.skills();

        if (skillRequests == null || skillRequests.isEmpty()) {
            return jobSkillNormalizationService.saveNormalizedSkills(
                    job,
                    request.title(),
                    request.description(),
                    request.roleDetail()
            );
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
            JobCreateRequest request
    ) {
        List<JobExperienceTagRequest> tagRequests = request.experienceTags();

        if (tagRequests == null || tagRequests.isEmpty()) {
            return jobExperienceTagNormalizationService.saveNormalizedExperienceTags(
                    job,
                    request.title(),
                    request.description(),
                    request.roleDetail()
            );
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
