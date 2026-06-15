package jobflow.domain.matching;

import java.util.List;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.JobRole;
import jobflow.domain.matching.dto.JdJobMatchResponse;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.BusinessException;
import jobflow.global.response.ApiResponse;
import jobflow.global.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class JdMatchController {

    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 50;

    private final JdMatchService jdMatchService;

    @GetMapping("/{userProjectId}/job-matches")
    public ResponseEntity<ApiResponse<List<JdJobMatchResponse>>> findProjectJobMatches(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long userProjectId,
            @RequestParam(required = false) List<JobRole> targetRoles,
            @RequestParam(required = false) CareerLevel targetCareerLevel,
            @RequestParam(defaultValue = "20") int limit
    ) {
        validateLimit(limit);

        List<JdJobMatchResponse> response = jdMatchService.findProjectJobMatches(
                principal.id(),
                userProjectId,
                targetRoles,
                targetCareerLevel,
                limit
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private void validateLimit(int limit) {
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new BusinessException(
                    ErrorCode.COMMON_INVALID_INPUT,
                    "limit은 1 이상 50 이하로 요청해야 합니다."
            );
        }
    }
}
