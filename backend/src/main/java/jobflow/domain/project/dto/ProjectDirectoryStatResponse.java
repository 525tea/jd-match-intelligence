package jobflow.domain.project.dto;

import java.io.Serializable;

public record ProjectDirectoryStatResponse(
        String path,
        int fileCount,
        int share
) implements Serializable {
}
