package jobflow.global.config;

import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("/v3/api-docs는 인증 없이 핵심 API path와 Bearer JWT scheme을 반환한다")
    void apiDocsExposeCorePathsAndBearerJwtScheme() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("JobFlow API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))

                .andExpect(jsonPath("$.paths", hasKey("/auth/login")))
                .andExpect(jsonPath("$.paths", hasKey("/auth/signup")))
                .andExpect(jsonPath("$.paths", hasKey("/auth/me")))
                .andExpect(jsonPath("$.paths", hasKey("/auth/oauth2/token")))

                .andExpect(jsonPath("$.paths", hasKey("/jobs")))
                .andExpect(jsonPath("$.paths", hasKey("/jobs/search")))
                .andExpect(jsonPath("$.paths", hasKey("/jobs/{jobId}")))
                .andExpect(jsonPath("$.paths", hasKey("/jobs/{jobId}/canonical-group")))

                .andExpect(jsonPath("$.paths", hasKey("/user/jobs/{jobId}/view")))
                .andExpect(jsonPath("$.paths", hasKey("/user/jobs/{jobId}/save")))
                .andExpect(jsonPath("$.paths['/user/jobs/{jobId}/save']", hasKey("post")))
                .andExpect(jsonPath("$.paths['/user/jobs/{jobId}/save']", hasKey("delete")))
                .andExpect(jsonPath("$.paths", hasKey("/user/jobs/{jobId}/ignore")))
                .andExpect(jsonPath("$.paths['/user/jobs/{jobId}/ignore']", hasKey("post")))
                .andExpect(jsonPath("$.paths['/user/jobs/{jobId}/ignore']", hasKey("delete")))
                .andExpect(jsonPath("$.paths", hasKey("/user/jobs/saved")))
                .andExpect(jsonPath("$.paths", hasKey("/user/jobs/viewed")))
                .andExpect(jsonPath("$.paths", hasKey("/user/jobs/ignored")))

                .andExpect(jsonPath("$.paths", hasKey("/applications")))
                .andExpect(jsonPath("$.paths", hasKey("/applications/{applicationId}")))
                .andExpect(jsonPath("$.paths", hasKey("/applications/{applicationId}/status")))
                .andExpect(jsonPath("$.paths", hasKey("/applications/{applicationId}/status-histories")))

                .andExpect(jsonPath("$.paths", hasKey("/projects/{userProjectId}/skills")))
                .andExpect(jsonPath("$.paths", hasKey("/projects/{userProjectId}/experience-tags")))
                .andExpect(jsonPath("$.paths", hasKey("/projects/{userProjectId}/job-matches")))
                .andExpect(jsonPath("$.paths", hasKey("/gap-analysis/projects/{userProjectId}")))
                .andExpect(jsonPath("$.paths", hasKey("/recommendations/jobs")))

                .andExpect(jsonPath("$.paths", hasKey("/trends/skills")))
                .andExpect(jsonPath("$.paths", hasKey("/trends/skills/{skillId}/cooccurrences")))
                .andExpect(jsonPath("$.paths", hasKey("/trends/skills/{skillId}/experience-tags")))
                .andExpect(jsonPath("$.paths", hasKey("/trends/market")));
    }

    @Test
    @DisplayName("/swagger-ui/index.html은 인증 없이 접근할 수 있다")
    void swaggerUiIndexIsPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
