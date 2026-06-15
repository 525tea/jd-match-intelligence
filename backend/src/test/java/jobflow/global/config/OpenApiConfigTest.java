package jobflow.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

    private final OpenApiConfig openApiConfig = new OpenApiConfig();

    @Test
    @DisplayName("OpenAPI 문서에 Bearer JWT security scheme을 등록한다")
    void jobFlowOpenAPIRegistersBearerJwtSecurityScheme() {
        OpenAPI openAPI = openApiConfig.jobFlowOpenAPI();

        SecurityScheme securityScheme = openAPI.getComponents()
                .getSecuritySchemes()
                .get(OpenApiConfig.BEARER_AUTH_SCHEME);

        assertThat(openAPI.getInfo().getTitle()).isEqualTo("JobFlow API");
        assertThat(securityScheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(securityScheme.getScheme()).isEqualTo("bearer");
        assertThat(securityScheme.getBearerFormat()).isEqualTo("JWT");
        assertThat(openAPI.getSecurity())
                .anySatisfy(requirement -> assertThat(requirement)
                        .containsKey(OpenApiConfig.BEARER_AUTH_SCHEME));
    }
}
