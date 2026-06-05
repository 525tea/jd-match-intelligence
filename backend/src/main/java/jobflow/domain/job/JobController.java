package jobflow.domain.job;

import jakarta.validation.Valid;
import jobflow.domain.job.dto.JobCreateRequest;
import jobflow.domain.job.dto.JobResponse;
import jobflow.domain.job.dto.JobSearchResponse;
import jobflow.domain.job.dto.JobSummaryResponse;
import jobflow.domain.job.dto.JobUpdateRequest;
import jobflow.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @PostMapping
    public ResponseEntity<ApiResponse<JobResponse>> createJob(
            @Valid @RequestBody JobCreateRequest request
    ) {
        JobResponse response = jobService.createJob(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<JobSummaryResponse>>> getJobs() {
        List<JobSummaryResponse> response = jobService.getJobs();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<JobSearchResponse>>> searchJobs(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "20") int limit
    ) {
        List<JobSearchResponse> response = jobService.searchJobs(keyword, limit);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<ApiResponse<JobResponse>> getJob(
            @PathVariable Long jobId
    ) {
        JobResponse response = jobService.getJob(jobId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{jobId}")
    public ResponseEntity<ApiResponse<JobResponse>> updateJob(
            @PathVariable Long jobId,
            @Valid @RequestBody JobUpdateRequest request
    ) {
        JobResponse response = jobService.updateJob(jobId, request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{jobId}/close")
    public ResponseEntity<ApiResponse<JobResponse>> closeJob(
            @PathVariable Long jobId
    ) {
        JobResponse response = jobService.closeJob(jobId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{jobId}/expire")
    public ResponseEntity<ApiResponse<JobResponse>> expireJob(
            @PathVariable Long jobId
    ) {
        JobResponse response = jobService.expireJob(jobId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
