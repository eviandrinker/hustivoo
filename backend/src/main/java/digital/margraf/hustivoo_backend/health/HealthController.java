package digital.margraf.hustivoo_backend.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

	@GetMapping("/api/v1/health")
	public HealthResponse health() {
		return new HealthResponse("UP", "Hustivoo", "Margraf Digital");
	}

	public record HealthResponse(String status, String application, String developer) {
	}
}
