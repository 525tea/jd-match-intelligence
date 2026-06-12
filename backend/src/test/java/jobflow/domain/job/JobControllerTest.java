package jobflow.domain.job;

import jobflow.domain.job.dto.JobExperienceTagResponse;
import jobflow.domain.job.dto.JobResponse;
import jobflow.domain.job.dto.JobSkillResponse;
import jobflow.domain.job.dto.JobSummaryResponse;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.GlobalExceptionHandler;
import jobflow.global.error.exception.ConflictException;
import jobflow.global.error.exception.EntityNotFoundException;
import jobflow.global.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import jobflow.domain.job.dto.JobSearchResponse;

@WebMvcTest(
        controllers = JobController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobService jobService;

    @Test
    @DisplayName("공고 생성 성공 시 201 ApiResponse를 반환한다")
    void createJob() throws Exception {
        given(jobService.createJob(any()))
                .willReturn(jobResponse(JobStatus.OPEN));

        mockMvc.perform(post("/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").value("백엔드 개발자"))
                .andExpect(jsonPath("$.data.companyName").value("JobFlow"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.skills", hasSize(1)))
                .andExpect(jsonPath("$.data.skills[0].name").value("Spring Boot"))
                .andExpect(jsonPath("$.data.experienceTags", hasSize(1)))
                .andExpect(jsonPath("$.data.experienceTags[0].code").value("HIGH_TRAFFIC"));
    }

    @Test
    @DisplayName("공고 생성 validation 실패 시 400 ErrorResponse를 반환한다")
    void createJobValidationFail() throws Exception {
        String requestBody = """
                {
                  "source": "",
                  "externalId": "external-1",
                  "title": "",
                  "companyName": "",
                  "description": "",
                  "url": "https://example.com/jobs/1",
                  "role": "BACKEND",
                  "roleDetail": "Java/Spring",
                  "careerLevel": "JUNIOR",
                  "minExperienceYears": -1,
                  "maxExperienceYears": -3,
                  "educationLevel": "BACHELOR",
                  "employmentType": "FULL_TIME",
                  "companySize": "STARTUP",
                  "industry": "IT",
                  "locationCountry": "",
                  "locationRegion": "Seoul",
                  "locationCity": "Gangnam",
                  "remoteType": "HYBRID",
                  "salaryMin": -3000,
                  "salaryMax": -5000,
                  "salaryCurrency": "",
                  "salaryVisible": true,
                  "hiringCount": -1,
                  "openedAt": "2026-06-01T09:00:00",
                  "deadlineAt": "2026-07-01T23:59:00"
                }
                """;

        mockMvc.perform(post("/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"));

        verifyNoInteractions(jobService);
    }

    @Test
    @DisplayName("존재하지 않는 스킬로 공고 생성 시 404 ErrorResponse를 반환한다")
    void createJobWithMissingSkill() throws Exception {
        willThrow(new EntityNotFoundException(ErrorCode.SKILL_NOT_FOUND))
                .given(jobService)
                .createJob(any());

        mockMvc.perform(post("/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SKILL_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("스킬을 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("존재하지 않는 경험 태그로 공고 생성 시 404 ErrorResponse를 반환한다")
    void createJobWithMissingExperienceTag() throws Exception {
        willThrow(new EntityNotFoundException(ErrorCode.EXPERIENCE_TAG_NOT_FOUND))
                .given(jobService)
                .createJob(any());

        mockMvc.perform(post("/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("EXPERIENCE_TAG_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("경험 태그를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("공고 상세 조회 성공 시 200 ApiResponse를 반환한다")
    void getJob() throws Exception {
        Long jobId = 1L;

        given(jobService.getJob(jobId))
                .willReturn(jobResponse(JobStatus.OPEN));

        mockMvc.perform(get("/jobs/{jobId}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").value("백엔드 개발자"))
                .andExpect(jsonPath("$.data.skills", hasSize(1)))
                .andExpect(jsonPath("$.data.experienceTags", hasSize(1)));
    }

    @Test
    @DisplayName("존재하지 않는 공고 조회 시 404 ErrorResponse를 반환한다")
    void getMissingJob() throws Exception {
        Long jobId = 999L;

        willThrow(new EntityNotFoundException(ErrorCode.JOB_NOT_FOUND))
                .given(jobService)
                .getJob(jobId);

        mockMvc.perform(get("/jobs/{jobId}", jobId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("JOB_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("공고를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("공고 목록 조회 성공 시 200 ApiResponse를 반환한다")
    void getJobs() throws Exception {
        given(jobService.getJobs())
                .willReturn(List.of(jobSummaryResponse()));

        mockMvc.perform(get("/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].title").value("백엔드 개발자"))
                .andExpect(jsonPath("$.data[0].status").value("OPEN"));
    }

    @Test
    @DisplayName("공고 검색 성공 시 score를 포함한 200 ApiResponse를 반환한다")
    void searchJobs() throws Exception {
        given(jobService.searchJobs("백엔드", 10))
                .willReturn(List.of(jobSearchResponse()));

        mockMvc.perform(get("/jobs/search")
                        .param("keyword", "백엔드")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].title").value("백엔드 개발자"))
                .andExpect(jsonPath("$.data[0].status").value("OPEN"))
                .andExpect(jsonPath("$.data[0].score").value(0.42));
    }

    @Test
    @DisplayName("공고 수정 성공 시 200 ApiResponse를 반환한다")
    void updateJob() throws Exception {
        Long jobId = 1L;

        given(jobService.updateJob(eq(jobId), any()))
                .willReturn(jobResponse(JobStatus.OPEN));

        mockMvc.perform(patch("/jobs/{jobId}", jobId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequestBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").value("백엔드 개발자"));
    }

    @Test
    @DisplayName("공고 종료 성공 시 200 ApiResponse를 반환한다")
    void closeJob() throws Exception {
        Long jobId = 1L;

        given(jobService.closeJob(jobId))
                .willReturn(jobResponse(JobStatus.CLOSED));

        mockMvc.perform(patch("/jobs/{jobId}/close", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
    }

    @Test
    @DisplayName("공고 만료 성공 시 200 ApiResponse를 반환한다")
    void expireJob() throws Exception {
        Long jobId = 1L;

        given(jobService.expireJob(jobId))
                .willReturn(jobResponse(JobStatus.EXPIRED));

        mockMvc.perform(patch("/jobs/{jobId}/expire", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("EXPIRED"));
    }

    @Test
    @DisplayName("이미 종료된 공고 상태 변경 시 409 ErrorResponse를 반환한다")
    void closeAlreadyClosedJob() throws Exception {
        Long jobId = 1L;

        willThrow(new ConflictException(ErrorCode.JOB_STATUS_CONFLICT))
                .given(jobService)
                .closeJob(jobId);

        mockMvc.perform(patch("/jobs/{jobId}/close", jobId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("JOB_STATUS_CONFLICT"))
                .andExpect(jsonPath("$.error.message").value("공고 상태를 변경할 수 없습니다."));
    }

    private String createRequestBody() {
        return """
                {
                  "source": "SARAMIN",
                  "externalId": "external-1",
                  "title": "백엔드 개발자",
                  "companyName": "JobFlow",
                  "description": "Spring Boot 기반 백엔드 개발자 채용",
                  "url": "https://example.com/jobs/1",
                  "role": "BACKEND",
                  "roleDetail": "Java/Spring",
                  "careerLevel": "JUNIOR",
                  "minExperienceYears": 1,
                  "maxExperienceYears": 3,
                  "educationLevel": "BACHELOR",
                  "employmentType": "FULL_TIME",
                  "companySize": "STARTUP",
                  "industry": "IT",
                  "locationCountry": "KR",
                  "locationRegion": "Seoul",
                  "locationCity": "Gangnam",
                  "remoteType": "HYBRID",
                  "salaryMin": 3000,
                  "salaryMax": 5000,
                  "salaryCurrency": "KRW",
                  "salaryVisible": true,
                  "hiringCount": 2,
                  "openedAt": "2026-06-01T09:00:00",
                  "deadlineAt": "2026-07-01T23:59:00",
                  "skills": [
                    {
                      "skillId": 1,
                      "requirementType": "REQUIRED"
                    }
                  ],
                  "experienceTags": [
                    {
                      "tagCode": "HIGH_TRAFFIC",
                      "sourcePhrase": "대용량 트래픽 처리 경험"
                    }
                  ]
                }
                """;
    }

    private String updateRequestBody() {
        return """
                {
                  "title": "백엔드 개발자",
                  "companyName": "JobFlow",
                  "description": "Spring Boot 기반 백엔드 개발자 채용",
                  "url": "https://example.com/jobs/1",
                  "role": "BACKEND",
                  "roleDetail": "Java/Spring",
                  "careerLevel": "JUNIOR",
                  "minExperienceYears": 1,
                  "maxExperienceYears": 3,
                  "educationLevel": "BACHELOR",
                  "employmentType": "FULL_TIME",
                  "companySize": "STARTUP",
                  "industry": "IT",
                  "locationCountry": "KR",
                  "locationRegion": "Seoul",
                  "locationCity": "Gangnam",
                  "remoteType": "HYBRID",
                  "salaryMin": 3000,
                  "salaryMax": 5000,
                  "salaryCurrency": "KRW",
                  "salaryVisible": true,
                  "hiringCount": 2,
                  "openedAt": "2026-06-01T09:00:00",
                  "deadlineAt": "2026-07-01T23:59:00"
                }
                """;
    }

    private JobResponse jobResponse(JobStatus status) {
        return new JobResponse(
                1L,
                "SARAMIN",
                "external-1",
                "백엔드 개발자",
                "JobFlow",
                "Spring Boot 기반 백엔드 개발자 채용",
                "https://example.com/jobs/1",
                JobRole.BACKEND,
                "Java/Spring",
                CareerLevel.JUNIOR,
                1,
                3,
                "BACHELOR",
                EmploymentType.FULL_TIME,
                "STARTUP",
                "IT",
                "KR",
                "Seoul",
                "Gangnam",
                RemoteType.HYBRID,
                3000,
                5000,
                "KRW",
                true,
                2,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 7, 1, 23, 59),
                status,
                List.of(new JobSkillResponse(
                        1L,
                        "Spring Boot",
                        "spring boot",
                        jobflow.domain.skill.SkillCategory.FRAMEWORK,
                        RequirementType.REQUIRED
                )),
                List.of(new JobExperienceTagResponse(
                        "HIGH_TRAFFIC",
                        "대용량 트래픽",
                        "대용량 트래픽 처리 경험",
                        "대용량 트래픽 처리 경험"
                ))
        );
    }

    private JobSummaryResponse jobSummaryResponse() {
        return new JobSummaryResponse(
                1L,
                "백엔드 개발자",
                "JobFlow",
                JobRole.BACKEND,
                CareerLevel.JUNIOR,
                EmploymentType.FULL_TIME,
                "Seoul",
                "Gangnam",
                RemoteType.HYBRID,
                LocalDateTime.of(2026, 7, 1, 23, 59),
                JobStatus.OPEN
        );
    }

    private JobSearchResponse jobSearchResponse() {
        return new JobSearchResponse(
                1L,
                "WANTED",
                "백엔드 개발자",
                "JobFlow",
                JobRole.BACKEND,
                CareerLevel.JUNIOR,
                EmploymentType.FULL_TIME,
                "Seoul",
                "Gangnam",
                RemoteType.HYBRID,
                LocalDateTime.of(2026, 7, 1, 23, 59),
                JobStatus.OPEN,
                0.42
        );
    }
}
