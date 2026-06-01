package jobflow.domain.skill;

import jakarta.validation.Valid;
import java.util.List;
import jobflow.domain.skill.dto.SkillCreateRequest;
import jobflow.domain.skill.dto.SkillResponse;
import jobflow.domain.skill.dto.SkillUpdateRequest;
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

@RestController
@RequestMapping("/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SkillResponse>>> findSkills(
            @RequestParam(required = false) SkillCategory category,
            @RequestParam(required = false) String keyword
    ) {
        List<SkillResponse> response = skillService.findSkills(category, keyword);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SkillResponse>> createSkill(
            @Valid @RequestBody SkillCreateRequest request
    ) {
        SkillResponse response = skillService.createSkill(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PatchMapping("/{skillId}")
    public ResponseEntity<ApiResponse<SkillResponse>> updateSkill(
            @PathVariable Long skillId,
            @Valid @RequestBody SkillUpdateRequest request
    ) {
        SkillResponse response = skillService.updateSkill(skillId, request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
