package jobflow.domain.analytics;

import java.time.LocalDate;
import java.util.List;
import jobflow.domain.analytics.dto.JobMarketStatsResponse;
import jobflow.domain.analytics.dto.SkillCooccurrenceResponse;
import jobflow.domain.analytics.dto.SkillExperienceMarketResponse;
import jobflow.domain.analytics.dto.SkillTrendResponse;
import jobflow.domain.job.JobRole;
import jobflow.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/trends")
@RequiredArgsConstructor
public class AnalyticsTrendController {

    private final AnalyticsTrendService analyticsTrendService;

    @GetMapping("/skills")
    public ResponseEntity<ApiResponse<List<SkillTrendResponse>>> getSkillTrends(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate month,
            @RequestParam(defaultValue = "20") int limit
    ) {
        List<SkillTrendResponse> response = analyticsTrendService.getSkillTrends(month, limit);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/skills/{skillId}/cooccurrences")
    public ResponseEntity<ApiResponse<List<SkillCooccurrenceResponse>>> getSkillCooccurrences(
            @PathVariable Long skillId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate month,
            @RequestParam(defaultValue = "20") int limit
    ) {
        List<SkillCooccurrenceResponse> response =
                analyticsTrendService.getSkillCooccurrences(month, skillId, limit);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/skills/{skillId}/experience-tags")
    public ResponseEntity<ApiResponse<List<SkillExperienceMarketResponse>>> getSkillExperienceMarkets(
            @PathVariable Long skillId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate month,
            @RequestParam(defaultValue = "20") int limit
    ) {
        List<SkillExperienceMarketResponse> response =
                analyticsTrendService.getSkillExperienceMarkets(month, skillId, limit);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/market")
    public ResponseEntity<ApiResponse<List<JobMarketStatsResponse>>> getJobMarketStats(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate month,
            @RequestParam JobRole role,
            @RequestParam(defaultValue = "20") int limit
    ) {
        List<JobMarketStatsResponse> response = analyticsTrendService.getJobMarketStats(month, role, limit);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
