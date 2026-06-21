package jobflow.domain.job.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.EmploymentType;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.RemoteType;

public record JobUpdateRequest(

        @NotBlank
        @Size(max = 255)
        String title,

        @NotBlank
        @Size(max = 255)
        String companyName,

        @NotBlank
        String description,

        @Size(max = 500)
        String url,

        @NotNull
        JobRole role,

        @Size(max = 100)
        String roleDetail,

        @NotNull
        CareerLevel careerLevel,

        @PositiveOrZero
        Integer minExperienceYears,

        @PositiveOrZero
        Integer maxExperienceYears,

        @Size(max = 30)
        String educationLevel,

        @NotNull
        EmploymentType employmentType,

        @Size(max = 30)
        String companySize,

        @Size(max = 100)
        String industry,

        @NotBlank
        @Size(max = 50)
        String locationCountry,

        @Size(max = 100)
        String locationRegion,

        @Size(max = 100)
        String locationCity,

        @NotNull
        RemoteType remoteType,

        @PositiveOrZero
        Integer salaryMin,

        @PositiveOrZero
        Integer salaryMax,

        @NotBlank
        @Size(min = 3, max = 3)
        String salaryCurrency,

        boolean salaryVisible,

        @PositiveOrZero
        Integer hiringCount,

        LocalDateTime openedAt,

        LocalDateTime deadlineAt,

        List<JobSkillRequest> skills,

        List<JobExperienceTagRequest> experienceTags
) {
}
