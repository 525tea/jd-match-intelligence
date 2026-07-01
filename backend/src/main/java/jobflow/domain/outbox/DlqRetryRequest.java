package jobflow.domain.outbox;

import tools.jackson.databind.JsonNode;

public record DlqRetryRequest(
        Integer schemaVersion,
        String sourceTopic,
        String sourceKey,
        OriginalPayload original
) {

    public record OriginalPayload(
            JsonNode payload
    ) {
    }
}
