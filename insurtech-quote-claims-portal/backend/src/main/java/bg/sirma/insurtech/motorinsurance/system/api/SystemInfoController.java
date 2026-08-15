package bg.sirma.insurtech.motorinsurance.system.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemInfoController {

    @GetMapping("/info")
    public SystemInfoResponse getSystemInfo() {
        return new SystemInfoResponse(
                "FOUNDATION_READY",
                "Motor Insurance Quote & Claims Portal",
                List.of("Java 21", "Spring Boot", "React", "PostgreSQL"));
    }

    public record SystemInfoResponse(String status, String project, List<String> stack) {
    }
}
