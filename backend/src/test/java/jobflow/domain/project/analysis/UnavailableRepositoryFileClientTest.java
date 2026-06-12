package jobflow.domain.project.analysis;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UnavailableRepositoryFileClientTest {

    private final UnavailableRepositoryFileClient client = new UnavailableRepositoryFileClient();

    @Test
    @DisplayName("실제 repository file client가 연결되기 전에는 조용히 빈 결과를 반환하지 않는다")
    void findFile() {
        assertThatThrownBy(() -> client.findFile(RepositoryRef.of("525tea", "jobflow"), "build.gradle"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_OAUTH2_PROVIDER_TOKEN_NOT_FOUND);
    }
}
