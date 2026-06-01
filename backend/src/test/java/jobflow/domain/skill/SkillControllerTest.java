package jobflow.domain.skill;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import jobflow.domain.skill.dto.SkillResponse;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.GlobalExceptionHandler;
import jobflow.global.error.exception.BusinessException;
import jobflow.global.error.exception.ConflictException;
import jobflow.global.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = SkillController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class SkillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SkillService skillService;

    @Test
    @DisplayName("스킬 목록 조회 시 200 ApiResponse를 반환한다")
    void findSkills() throws Exception {
        given(skillService.findSkills(null, null))
                .willReturn(List.of(
                        new SkillResponse(1L, "Java", "java", SkillCategory.LANGUAGE),
                        new SkillResponse(2L, "Spring Boot", "spring boot", SkillCategory.FRAMEWORK)
                ));

        mockMvc.perform(get("/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Java"))
                .andExpect(jsonPath("$.data[0].normalizedName").value("java"))
                .andExpect(jsonPath("$.data[0].category").value("LANGUAGE"))
                .andExpect(jsonPath("$.data[1].id").value(2))
                .andExpect(jsonPath("$.data[1].name").value("Spring Boot"))
                .andExpect(jsonPath("$.data[1].normalizedName").value("spring boot"))
                .andExpect(jsonPath("$.data[1].category").value("FRAMEWORK"));
    }

    @Test
    @DisplayName("카테고리와 키워드로 스킬 목록을 조회한다")
    void findSkillsWithCondition() throws Exception {
        given(skillService.findSkills(SkillCategory.FRAMEWORK, "spring"))
                .willReturn(List.of(
                        new SkillResponse(1L, "Spring Boot", "spring boot", SkillCategory.FRAMEWORK)
                ));

        mockMvc.perform(get("/skills")
                        .param("category", "FRAMEWORK")
                        .param("keyword", "spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("Spring Boot"))
                .andExpect(jsonPath("$.data[0].category").value("FRAMEWORK"));
    }

    @Test
    @DisplayName("스킬 등록 성공 시 201 ApiResponse를 반환한다")
    void createSkill() throws Exception {
        String requestBody = """
                {
                  "name": "Spring Boot",
                  "normalizedName": "spring boot",
                  "category": "FRAMEWORK"
                }
                """;

        given(skillService.createSkill(any()))
                .willReturn(new SkillResponse(
                        1L,
                        "Spring Boot",
                        "spring boot",
                        SkillCategory.FRAMEWORK
                ));

        mockMvc.perform(post("/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Spring Boot"))
                .andExpect(jsonPath("$.data.normalizedName").value("spring boot"))
                .andExpect(jsonPath("$.data.category").value("FRAMEWORK"));
    }

    @Test
    @DisplayName("스킬 등록 validation 실패 시 400 ErrorResponse를 반환한다")
    void createSkillValidationFail() throws Exception {
        String requestBody = """
                {
                  "name": "",
                  "normalizedName": "",
                  "category": null
                }
                """;

        mockMvc.perform(post("/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
                .andExpect(jsonPath("$.error.fields", hasSize(3)));

        verifyNoInteractions(skillService);
    }

    @Test
    @DisplayName("중복 스킬 등록 시 409 ErrorResponse를 반환한다")
    void createDuplicatedSkill() throws Exception {
        String requestBody = """
                {
                  "name": "Spring Boot",
                  "normalizedName": "spring boot",
                  "category": "FRAMEWORK"
                }
                """;

        willThrow(new ConflictException(ErrorCode.SKILL_ALREADY_EXISTS))
                .given(skillService)
                .createSkill(any());

        mockMvc.perform(post("/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SKILL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.error.message").value("이미 등록된 스킬입니다."));
    }

    @Test
    @DisplayName("스킬 수정 성공 시 200 ApiResponse를 반환한다")
    void updateSkill() throws Exception {
        Long skillId = 1L;
        String requestBody = """
                {
                  "name": "Spring Boot",
                  "normalizedName": "spring boot",
                  "category": "FRAMEWORK"
                }
                """;

        given(skillService.updateSkill(eq(skillId), any()))
                .willReturn(new SkillResponse(
                        skillId,
                        "Spring Boot",
                        "spring boot",
                        SkillCategory.FRAMEWORK
                ));

        mockMvc.perform(patch("/skills/{skillId}", skillId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Spring Boot"))
                .andExpect(jsonPath("$.data.normalizedName").value("spring boot"))
                .andExpect(jsonPath("$.data.category").value("FRAMEWORK"));
    }

    @Test
    @DisplayName("스킬 수정 validation 실패 시 400 ErrorResponse를 반환한다")
    void updateSkillValidationFail() throws Exception {
        String requestBody = """
                {
                  "name": "",
                  "normalizedName": "",
                  "category": null
                }
                """;

        mockMvc.perform(patch("/skills/{skillId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
                .andExpect(jsonPath("$.error.fields", hasSize(3)));

        verifyNoInteractions(skillService);
    }

    @Test
    @DisplayName("존재하지 않는 스킬 수정 시 404 ErrorResponse를 반환한다")
    void updateMissingSkill() throws Exception {
        Long skillId = 999L;
        String requestBody = """
                {
                  "name": "Spring Boot",
                  "normalizedName": "spring boot",
                  "category": "FRAMEWORK"
                }
                """;

        willThrow(new BusinessException(ErrorCode.SKILL_NOT_FOUND))
                .given(skillService)
                .updateSkill(eq(skillId), any());

        mockMvc.perform(patch("/skills/{skillId}", skillId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SKILL_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("스킬을 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("중복 스킬 수정 시 409 ErrorResponse를 반환한다")
    void updateDuplicatedSkill() throws Exception {
        Long skillId = 1L;
        String requestBody = """
                {
                  "name": "Java",
                  "normalizedName": "java",
                  "category": "LANGUAGE"
                }
                """;

        willThrow(new ConflictException(ErrorCode.SKILL_ALREADY_EXISTS))
                .given(skillService)
                .updateSkill(eq(skillId), any());

        mockMvc.perform(patch("/skills/{skillId}", skillId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SKILL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.error.message").value("이미 등록된 스킬입니다."));
    }
}
