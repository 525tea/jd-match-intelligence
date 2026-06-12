package jobflow.domain.gap;

import java.util.List;
import jobflow.domain.gap.dto.GapAnalysisResponse;
import jobflow.domain.job.JobRole;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.BusinessException;
import jobflow.global.response.ApiResponse;
import jobflow.global.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/gap-analysis")
public class GapAnalysisController {

    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 50;

    private final GapAnalysisService gapAnalysisService;

    public GapAnalysisController(GapAnalysisService gapAnalysisService) {
        this.gapAnalysisService = gapAnalysisService;
    }

    @GetMapping("/projects/{userProjectId}")
    public ResponseEntity<ApiResponse<GapAnalysisResponse>> analyzeProjectSkillGap(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long userProjectId,
            @RequestParam(required = false) List<JobRole> targetRoles,
            @RequestParam(defaultValue = "20") int limit
    ) {
        validateLimit(limit);

        GapAnalysisResponse response = gapAnalysisService.analyzeProjectSkillGap(
                principal.id(),
                userProjectId,
                targetRoles,
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
