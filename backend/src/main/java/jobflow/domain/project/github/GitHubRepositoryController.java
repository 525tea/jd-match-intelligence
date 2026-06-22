package jobflow.domain.project.github;

import jakarta.validation.Valid;
import java.util.List;
import jobflow.global.response.ApiResponse;
import jobflow.global.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class GitHubRepositoryController {

    private final GitHubRepositoryAnalysisService gitHubRepositoryAnalysisService;

    @GetMapping("/github/repositories")
    public ResponseEntity<ApiResponse<List<GitHubRepositoryResponse>>> getRepositories(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<GitHubRepositoryResponse> response =
                gitHubRepositoryAnalysisService.listRepositories(principal.id());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/projects/github-import")
    public ResponseEntity<ApiResponse<GitHubRepositoryImportResponse>> importRepository(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody GitHubRepositoryImportRequest request
    ) {
        GitHubRepositoryImportResponse response =
                gitHubRepositoryAnalysisService.importRepository(principal.id(), request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
