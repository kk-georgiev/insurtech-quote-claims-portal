package bg.sirma.insurtech.motorinsurance.system.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SystemInfoControllerTest {

    private final SystemInfoController controller = new SystemInfoController();

    @Test
    void shouldDescribeTheFoundationWithoutStartingInfrastructure() {
        var response = controller.getSystemInfo();

        assertThat(response.status()).isEqualTo("FOUNDATION_READY");
        assertThat(response.project()).contains("Motor Insurance");
        assertThat(response.stack()).contains("Java 21", "React", "PostgreSQL");
    }
}
