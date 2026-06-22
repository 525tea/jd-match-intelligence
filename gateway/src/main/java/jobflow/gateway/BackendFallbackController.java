package jobflow.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

@RestController
public class BackendFallbackController {

    @RequestMapping("/fallback/backend")
    public Mono<ResponseEntity<Map<String, Object>>> backendFallback() {
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "success", false,
                        "error", Map.of(
                                "code", "GATEWAY_BACKEND_UNAVAILABLE",
                                "message", "백엔드 서비스를 일시적으로 사용할 수 없습니다."
                        ),
                        "timestamp", Instant.now().toString()
                )));
    }
}
