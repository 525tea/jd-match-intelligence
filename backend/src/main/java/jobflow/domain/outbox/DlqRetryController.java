package jobflow.domain.outbox;

import jobflow.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/dlq")
@RequiredArgsConstructor
public class DlqRetryController {

    private final KafkaDlqRetryService kafkaDlqRetryService;

    @PostMapping("/retry")
    public ResponseEntity<ApiResponse<DlqRetryResponse>> retry(
            @RequestBody DlqRetryRequest request
    ) {
        DlqRetryResponse response = kafkaDlqRetryService.retry(request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
