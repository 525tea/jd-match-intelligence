package jobflow.domain.project;

import java.util.List;
import jobflow.domain.project.dto.ProjectExperienceTagInventoryResponse;
import jobflow.domain.project.dto.ProjectSummaryResponse;
import jobflow.domain.project.dto.ProjectSkillInventoryResponse;
import jobflow.global.response.ApiResponse;
import jobflow.global.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectInventoryController {

    private final ProjectInventoryService projectInventoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectSummaryResponse>>> getUserProjects(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<ProjectSummaryResponse> response =
                projectInventoryService.getUserProjects(principal.id());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{userProjectId}/skills")
    public ResponseEntity<ApiResponse<List<ProjectSkillInventoryResponse>>> getProjectSkills(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long userProjectId
    ) {
        List<ProjectSkillInventoryResponse> response =
                projectInventoryService.getProjectSkills(principal.id(), userProjectId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{userProjectId}")
    public ResponseEntity<ApiResponse<ProjectSummaryResponse>> getProjectSummary(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long userProjectId
    ) {
        ProjectSummaryResponse response =
                projectInventoryService.getProjectSummary(principal.id(), userProjectId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{userProjectId}/experience-tags")
    public ResponseEntity<ApiResponse<List<ProjectExperienceTagInventoryResponse>>> getProjectExperienceTags(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long userProjectId
    ) {
        List<ProjectExperienceTagInventoryResponse> response =
                projectInventoryService.getProjectExperienceTags(principal.id(), userProjectId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
