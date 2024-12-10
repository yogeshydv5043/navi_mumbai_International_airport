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

@QuarkusTest
class BhsIbAdapterInQueueTest extends CamelQuarkusTestSupport {

    @Inject
    CamelContext camelContext;

    private String xmlFlightLegNotifRQ;
    private String xmlHealthCheck;
    private String xmlFlightLegRQ;

    @BeforeEach
    void setup() throws Exception {
        xmlFlightLegNotifRQ = Files.readString(Paths.get("src/main/resources/XSD/IATA_AIDX_FlightLegNotifRQ.xml"));
        xmlHealthCheck = Files.readString(Paths.get("src/main/resources/XSD/HealthCheckRequest.xml"));
        xmlFlightLegRQ = Files.readString(Paths.get("src/main/resources/XSD/IATA_AIDX_FlightLegRQ.xml"));
        // Add routes
        camelContext.addRoutes(new BhsIbAdapterInQueue());

        // Advise routes
        setupRoutes();

        // Start Camel context after advice
        camelContext.start();

        // Create a producer template if not already created
        if (template == null) {
            template = camelContext.createProducerTemplate();
        }
    }

    private void setupRoutes() throws Exception {
        // Advise the main route to replace the 'from' endpoint
        AdviceWith.adviceWith(camelContext, "BHSAdapterInQueue", route -> {
            route.replaceFromWith("direct:BHSInQueue");
        });

        // Advise the processor route and mock endpoints
        AdviceWith.adviceWith(camelContext, "BhsRequestProcessor", route -> {
            route.weaveByToUri("direct:bhs-flightlegrq-processor")
                    .replace()
                    .to("mock:direct:bhs-flightlegrq-processor");
            route.weaveByToUri("direct:bhs-healthcheck-processor")
                    .replace().
                    to("mock:direct:bhs-healthcheck-processor");
            route.weaveByToUri("direct:bhs-flightlegnotifrq-processor")
                    .replace()
                    .to("mock:direct:bhs-flightlegnotifrq-processor");
            route.weaveByToUri("seda:save-log-into-db")
                    .replace()
                    .to("mock:seda:save-log-into-db");
        });
    }

    @Test
    void FlightLegNotifRQScenarioTest() throws InterruptedException {
        // Mock endpoints
        MockEndpoint mockFlightLeg = getMockEndpoint("mock:direct:bhs-flightlegrq-processor");
        MockEndpoint mockFlightLegNotifRQ = getMockEndpoint("mock:direct:bhs-flightlegnotifrq-processor");
        MockEndpoint mockHealthCheck = getMockEndpoint("mock:direct:bhs-healthcheck-processor");
        MockEndpoint mockSaveLog = getMockEndpoint("mock:seda:save-log-into-db");

        // Set expectations
        mockFlightLeg.expectedMessageCount(0);
        mockFlightLegNotifRQ.expectedMessageCount(1);
        mockHealthCheck.expectedMessageCount(0);
        mockSaveLog.expectedMessageCount(1);

        // Send the test message
        template.sendBody("direct:BHSInQueue", xmlFlightLegNotifRQ);

        // Assert the expectations
        mockFlightLeg.assertIsSatisfied();
        mockFlightLegNotifRQ.assertIsSatisfied();
        mockHealthCheck.assertIsSatisfied();
        mockSaveLog.assertIsSatisfied();
    }

    @Test
    void FlightLegRQScenarioTest() throws InterruptedException {
        // Mock endpoints
        MockEndpoint mockFlightLegRQ = getMockEndpoint("mock:direct:bhs-flightlegrq-processor");
        MockEndpoint mockFlightLegNotifRQ = getMockEndpoint("mock:direct:bhs-flightlegnotifrq-processor");
        MockEndpoint mockHealthCheck = getMockEndpoint("mock:direct:bhs-healthcheck-processor");
        MockEndpoint mockSaveLog = getMockEndpoint("mock:seda:save-log-into-db");

        // Set expectations
        mockFlightLegRQ.expectedMessageCount(1);
        mockFlightLegNotifRQ.expectedMessageCount(0);
        mockHealthCheck.expectedMessageCount(0);
        mockSaveLog.expectedMessageCount(1);

        // Send the test message
        template.sendBody("direct:BHSInQueue", xmlFlightLegRQ);

        // Assert the expectations
        mockFlightLegRQ.assertIsSatisfied();
        mockFlightLegNotifRQ.assertIsSatisfied();
        mockHealthCheck.assertIsSatisfied();
        mockSaveLog.assertIsSatisfied();
    }

    @Test
    void HealthCheckScenarioTest() throws InterruptedException {
        // Mock endpoints
        MockEndpoint mockFlightLegRQ = getMockEndpoint("mock:direct:bhs-flightlegrq-processor");
        MockEndpoint mockFlightLegNotifRQ = getMockEndpoint("mock:direct:bhs-flightlegnotifrq-processor");
        MockEndpoint mockHealthCheck = getMockEndpoint("mock:direct:bhs-healthcheck-processor");
        MockEndpoint mockSaveLog = getMockEndpoint("mock:seda:save-log-into-db");

        // Set expectations
        mockFlightLegRQ.expectedMessageCount(0);
        mockFlightLegNotifRQ.expectedMessageCount(0);
        mockHealthCheck.expectedMessageCount(1);
        mockSaveLog.expectedMessageCount(1);

        // Send the test message
        template.sendBody("direct:BHSInQueue", xmlHealthCheck);

        // Assert the expectations
        mockFlightLegRQ.assertIsSatisfied();
        mockFlightLegNotifRQ.assertIsSatisfied();
        mockHealthCheck.assertIsSatisfied();
        mockSaveLog.assertIsSatisfied();
    }

}
