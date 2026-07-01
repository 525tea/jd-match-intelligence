package jobflow.domain.outbox;

import java.util.List;
import jobflow.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/dlq")
@RequiredArgsConstructor
public class DlqRetryController {

    private final KafkaDlqRetryService kafkaDlqRetryService;
    private final DlqMessageService dlqMessageService;

    @GetMapping("/messages")
    public ResponseEntity<ApiResponse<List<DlqMessageResponse>>> findMessages() {
        return ResponseEntity.ok(ApiResponse.success(dlqMessageService.findMessages()));
    }

    @GetMapping("/messages/{messageId}")
    public ResponseEntity<ApiResponse<DlqMessageDetailResponse>> getMessage(
            @PathVariable Long messageId
    ) {
        return ResponseEntity.ok(ApiResponse.success(dlqMessageService.getMessage(messageId)));
    }

    @PostMapping("/messages/{messageId}/retry")
    public ResponseEntity<ApiResponse<DlqRetryResponse>> retryById(
            @PathVariable Long messageId
    ) {
        DlqRetryResponse response = dlqMessageService.retry(messageId);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/retry")
    public ResponseEntity<ApiResponse<DlqRetryResponse>> retry(
            @RequestBody DlqRetryRequest request
    ) {
        DlqRetryResponse response = kafkaDlqRetryService.retry(request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
