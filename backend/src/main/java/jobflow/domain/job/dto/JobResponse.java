package jobflow.domain.job.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.EmploymentType;
import jobflow.domain.job.Job;
import jobflow.domain.job.JobDescriptionSectionParser;
import jobflow.domain.job.JobExperienceTag;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.JobSkill;
import jobflow.domain.job.JobStatus;
import jobflow.domain.job.RemoteType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public record JobResponse(
        Long id,
        String source,
        String externalId,
        String canonicalFingerprint,
        String title,
        String companyName,
        String description,
        String url,
        String originalUrl,
        String applyUrl,
        List<JobDescriptionSectionResponse> descriptionSections,
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
        LocalDateTime deadlineAt,
        JobStatus status,
        List<JobSkillResponse> skills,
        List<JobExperienceTagResponse> experienceTags
) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static JobResponse of(
            Job job,
            List<JobSkill> jobSkills,
            List<JobExperienceTag> jobExperienceTags,
            String applyUrl
    ) {
        return new JobResponse(
                job.getId(),
                job.getSource(),
                job.getExternalId(),
                job.getCanonicalFingerprint(),
                job.getTitle(),
                job.getCompanyName(),
                job.getDescription(),
                job.getUrl(),
                job.getOriginalUrl(),
                applyUrl,
                descriptionSections(job),
                job.getRole(),
                job.getRoleDetail(),
                job.getCareerLevel(),
                job.getMinExperienceYears(),
                job.getMaxExperienceYears(),
                job.getEducationLevel(),
                job.getEmploymentType(),
                job.getCompanySize(),
                job.getIndustry(),
                job.getLocationCountry(),
                job.getLocationRegion(),
                job.getLocationCity(),
                job.getRemoteType(),
                job.getSalaryMin(),
                job.getSalaryMax(),
                job.getSalaryCurrency(),
                job.isSalaryVisible(),
                job.getHiringCount(),
                job.getOpenedAt(),
                job.getDeadlineAt(),
                job.getStatus(),
                jobSkills.stream()
                        .map(JobSkillResponse::from)
                        .toList(),
                jobExperienceTags.stream()
                        .map(JobExperienceTagResponse::from)
                        .toList()
        );
    }

    private static List<JobDescriptionSectionResponse> descriptionSections(Job job) {
        List<JobDescriptionSectionResponse> storedSections = parseStoredDescriptionSections(
                job.getDescriptionSections()
        );

        if (!storedSections.isEmpty()) {
            return storedSections;
        }

        return new JobDescriptionSectionParser().parse(job.getDescription());
    }

    private static List<JobDescriptionSectionResponse> parseStoredDescriptionSections(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(value);

            if (!root.isArray()) {
                return List.of();
            }

            List<JobDescriptionSectionResponse> sections = new ArrayList<>();

            for (JsonNode node : root) {
                String type = node.path("type").asText("");
                String title = node.path("title").asText("");
                String body = node.path("body").asText("");

                if (!title.isBlank() && !body.isBlank()) {
                    sections.add(new JobDescriptionSectionResponse(type, title, body));
                }
            }

            return sections;
        } catch (JacksonException exception) {
            return List.of();
        }
    }
}
