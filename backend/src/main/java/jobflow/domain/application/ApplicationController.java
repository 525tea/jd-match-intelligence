package jobflow.domain.application;

import jakarta.validation.Valid;
import java.util.List;
import jobflow.domain.application.dto.ApplicationCreateRequest;
import jobflow.domain.application.dto.ApplicationResponse;
import jobflow.domain.application.dto.ApplicationStatusUpdateRequest;
import jobflow.domain.application.dto.ApplicationSummaryResponse;
import jobflow.global.response.ApiResponse;
import jobflow.global.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    @PostMapping
    public ResponseEntity<ApiResponse<ApplicationResponse>> createApplication(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ApplicationCreateRequest request
    ) {
        ApplicationResponse response = applicationService.createApplication(
                principal.id(),
                request
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/{applicationId}")
    public ResponseEntity<ApiResponse<ApplicationResponse>> getApplication(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long applicationId
    ) {
        ApplicationResponse response = applicationService.getApplication(
                principal.id(),
                applicationId
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ApplicationSummaryResponse>>> getMyApplications(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<ApplicationSummaryResponse> response = applicationService.getMyApplications(
                principal.id()
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{applicationId}/status")
    public ResponseEntity<ApiResponse<ApplicationResponse>> updateApplicationStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long applicationId,
            @Valid @RequestBody ApplicationStatusUpdateRequest request
    ) {
        ApplicationResponse response = applicationService.updateApplicationStatus(
                principal.id(),
                applicationId,
                request
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
