package jobflow.domain.userjob;

import jobflow.domain.userjob.dto.UserJobResponse;
import jobflow.global.response.ApiResponse;
import jobflow.global.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user/jobs")
@RequiredArgsConstructor
public class UserJobController {

    private final UserJobService userJobService;

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
