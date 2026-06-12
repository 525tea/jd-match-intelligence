package jobflow.domain.analytics;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class JobSkillIndexRebuildRunnerTest {

    @Test
    @DisplayName("job skill index rebuild runner는 rebuild service를 실행한다")
    void run() {
        JobSkillIndexRebuildService service = mock(JobSkillIndexRebuildService.class);
        when(service.rebuild()).thenReturn(new JobSkillIndexRebuildResult(10, 10));

        JobSkillIndexRebuildRunner runner = new JobSkillIndexRebuildRunner(service);

        runner.run(new DefaultApplicationArguments());

        verify(service).rebuild();
    }
}
