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
class BhsFlightLegNotifRQTest extends CamelQuarkusTestSupport {

    private String xmlFlightLeg;

    @Inject
    private CamelContext camelContext;

    @BeforeEach
    public void setUp() throws Exception {
        xmlFlightLeg = Files.readString(Paths.get("src/main/resources/XSD/IATA_AIDX_FlightLegNotifRQ.xml"));

        camelContext.addRoutes(new BhsFlightLegNotifRQ());
        camelContext.start();

        // Manually create the ProducerTemplate
        if (template == null) {
            template = camelContext.createProducerTemplate();
        }

        AdviceWith.adviceWith(camelContext, "bhsFlightLegNotifRQProcessor", route -> {
            route.interceptSendToEndpoint("{{route.flightLegNotifRQEndpoint}}")
                    .skipSendToOriginalEndpoint()
                    .to("mock:flightLegNotifRQEndpoint");
            route.mockEndpointsAndSkip("{{route.ackOutQueue}}");
            route.mockEndpoints("seda:save-log-into-db");
        });
    }

    @Test
    void SuccessScenarioTest() throws InterruptedException {
        // Mock endpoints
        MockEndpoint mockFlightLegNotifRQEndpoint = getMockEndpoint("mock:flightLegNotifRQEndpoint");
        MockEndpoint mockAckOutQueue = getMockEndpoint("mock:{{route.ackOutQueue}}");
        MockEndpoint mockSaveLogDb = getMockEndpoint("mock:seda:save-log-into-db");

        // Expectations
        mockFlightLegNotifRQEndpoint.expectedMessageCount(1);
        mockAckOutQueue.expectedMessageCount(1);
        mockSaveLogDb.expectedMessageCount(1);

        // Send a test message with validationStatus as "SUCCESS"
        template.sendBodyAndHeader("direct:bhs-flightlegnotifrq-processor", xmlFlightLeg, "validationStatus", "SUCCESS");

        // Assertions
        mockFlightLegNotifRQEndpoint.assertIsSatisfied();
        mockAckOutQueue.assertIsSatisfied();
        mockSaveLogDb.assertIsSatisfied();

        // Check if the correct properties are set for success
        String errorDesc = mockSaveLogDb.getExchanges().get(0).getProperty("error_desc", String.class);

        assertEquals("", errorDesc, "The error description should be empty for success.");

    }

    @Test
    void FailureScenarioTest() throws InterruptedException {
        // Mock endpoints
        MockEndpoint mockFlightLegNotifRQEndpoint = getMockEndpoint("mock:flightLegNotifRQEndpoint");
        MockEndpoint mockAckOutQueue = getMockEndpoint("mock:{{route.ackOutQueue}}");
        MockEndpoint mockSaveLogDb = getMockEndpoint("mock:seda:save-log-into-db");

        // Expectations
        mockFlightLegNotifRQEndpoint.expectedMessageCount(1);
        mockAckOutQueue.expectedMessageCount(1);
        mockSaveLogDb.expectedMessageCount(1);

        // Send a test message with validationStatus as "SUCCESS"
        template.sendBodyAndHeader("direct:bhs-flightlegnotifrq-processor", xmlFlightLeg, "validationStatus", "Failure");

        // Assertions
        mockFlightLegNotifRQEndpoint.assertIsSatisfied();
        mockAckOutQueue.assertIsSatisfied();
        mockSaveLogDb.assertIsSatisfied();

        // Check if the correct properties are set for success
        String errorDesc = mockSaveLogDb.getExchanges().get(0).getProperty("error_desc", String.class);

        assertEquals("Message type is invalid", errorDesc);

    }


}