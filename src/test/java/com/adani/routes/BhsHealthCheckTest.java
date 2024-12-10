package com.adani.routes;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class BhsHealthCheckTest extends CamelQuarkusTestSupport {

    private String xmlHealthCheck;

    @Inject
    private CamelContext camelContext;

    @BeforeEach
    public void setUp() throws Exception {
        xmlHealthCheck = Files.readString(Paths.get("src/main/resources/XSD/HealthCheckRequest.xml"));

        camelContext.addRoutes(new BhsHealthCheck());
        camelContext.start();

        // Manually create the ProducerTemplate
        if (template == null) {
            template = camelContext.createProducerTemplate();
        }

        AdviceWith.adviceWith(camelContext, "bhsHealthCheckProcessor", route -> {
            route.replaceFromWith("direct:healthcheck-processor");
            route.interceptSendToEndpoint("{{route.healthCheckEndpoint}}")
                    .skipSendToOriginalEndpoint()
                    .to("mock:route.healthCheckEndpoint");
            route.mockEndpointsAndSkip("{{route.ackOutQueue}}");
            route.mockEndpoints("seda:save-log-into-db");

        });
    }

    @Test
    void FailureScenarioTest() throws InterruptedException {
        // Mock endpoints
        MockEndpoint mockHealthCheckEndpoint = getMockEndpoint("mock:route.healthCheckEndpoint");
        MockEndpoint mockAckOutQueue = getMockEndpoint("mock:{{route.ackOutQueue}}");
        MockEndpoint mockSaveLogDb = getMockEndpoint("mock:seda:save-log-into-db");

        // Expectations
        mockHealthCheckEndpoint.expectedMessageCount(1);
        mockAckOutQueue.expectedMessageCount(1);
        mockSaveLogDb.expectedMessageCount(1);

        // Send a test message with validationStatus as "SUCCESS"
        template.sendBodyAndHeader("direct:healthcheck-processor", xmlHealthCheck, "validationStatus", "Failure");

        // Assertions
        mockHealthCheckEndpoint.assertIsSatisfied();
        mockAckOutQueue.assertIsSatisfied();
        mockSaveLogDb.assertIsSatisfied();

        // Check if the correct properties are set for success
        String processStatus = mockSaveLogDb.getExchanges().get(0).getProperty("ib_process_status", String.class);

        assertEquals("Failure", processStatus, "The error description should be Failure.");

    }

    @Test
    void SuccessScenarioTest() throws InterruptedException {
        // Mock endpoints
        MockEndpoint mockHealthCheckEndpoint = getMockEndpoint("mock:route.healthCheckEndpoint");
        MockEndpoint mockAckOutQueue = getMockEndpoint("mock:{{route.ackOutQueue}}");
        MockEndpoint mockSaveLogDb = getMockEndpoint("mock:seda:save-log-into-db");

        // Expectations
        mockHealthCheckEndpoint.expectedMessageCount(1);
        mockAckOutQueue.expectedMessageCount(1);
        mockSaveLogDb.expectedMessageCount(1);

        // Send a test message with validationStatus as "SUCCESS"
        template.sendBodyAndHeader("direct:healthcheck-processor", xmlHealthCheck, "validationStatus", "Success");

        // Assertions
        mockHealthCheckEndpoint.assertIsSatisfied();
        mockAckOutQueue.assertIsSatisfied();
        mockSaveLogDb.assertIsSatisfied();

        // Check if the correct properties are set for success
        String processStatus = mockSaveLogDb.getExchanges().get(0).getProperty("ib_process_status", String.class);

        assertEquals("Success", processStatus, "The error description should be Failure.");

    }


    @Test
    void SERVICE_INACTIVEScenarioTest() throws InterruptedException {
        // Mock endpoints
        MockEndpoint mockHealthCheckEndpoint = getMockEndpoint("mock:route.healthCheckEndpoint");
        MockEndpoint mockAckOutQueue = getMockEndpoint("mock:{{route.ackOutQueue}}");
        MockEndpoint mockSaveLogDb = getMockEndpoint("mock:seda:save-log-into-db");

        // Expectations
        mockHealthCheckEndpoint.expectedMessageCount(1);
        mockAckOutQueue.expectedMessageCount(1);
        mockSaveLogDb.expectedMessageCount(1);

        // Send a test message with validationStatus as "SUCCESS"
        template.sendBodyAndHeader("direct:healthcheck-processor", xmlHealthCheck, "validationStatus", "SERVICE_INACTIVE");

        // Assertions
        mockHealthCheckEndpoint.assertIsSatisfied();
        mockAckOutQueue.assertIsSatisfied();
        mockSaveLogDb.assertIsSatisfied();

        // Check if the correct properties are set for success
        String processStatus = mockSaveLogDb.getExchanges().get(0).getProperty("ib_process_status", String.class);

        assertEquals("Failure", processStatus, "The error description should be Failure.");

    }

}