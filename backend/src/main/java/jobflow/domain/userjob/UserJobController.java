package jobflow.domain.userjob;

import jobflow.domain.userjob.dto.UserJobResponse;
import jobflow.global.response.ApiResponse;
import jobflow.global.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/jobs")
@RequiredArgsConstructor
public class UserJobController {

    private final UserJobService userJobService;

    @GetMapping("/viewed")
    public ResponseEntity<ApiResponse<List<UserJobResponse>>> getMyViewedJobs(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<UserJobResponse> response = userJobService.getMyViewedJobs(principal.id());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/saved")
    public ResponseEntity<ApiResponse<List<UserJobResponse>>> getMySavedJobs(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<UserJobResponse> response = userJobService.getMySavedJobs(principal.id());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/ignored")
    public ResponseEntity<ApiResponse<List<UserJobResponse>>> getMyIgnoredJobs(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<UserJobResponse> response = userJobService.getMyIgnoredJobs(principal.id());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<ApiResponse<UserJobResponse>> getMyJob(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long jobId
    ) {
        UserJobResponse response = userJobService.getMyJob(principal.id(), jobId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{jobId}/view")
    public ResponseEntity<ApiResponse<UserJobResponse>> markViewed(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long jobId
    ) {
        UserJobResponse response = userJobService.markViewed(principal.id(), jobId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{jobId}/save")
    public ResponseEntity<ApiResponse<UserJobResponse>> saveJob(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long jobId
    ) {
        UserJobResponse response = userJobService.saveJob(principal.id(), jobId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{jobId}/ignore")
    public ResponseEntity<ApiResponse<UserJobResponse>> ignoreJob(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long jobId
    ) {
        UserJobResponse response = userJobService.ignoreJob(principal.id(), jobId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
