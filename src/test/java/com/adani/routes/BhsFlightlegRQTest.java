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
class BhsFlightlegRQTest extends CamelQuarkusTestSupport {

    private String xmlFlightLeg;

    @Inject
    private CamelContext camelContext;

    @BeforeEach
    public void setUp() throws Exception {
        xmlFlightLeg = Files.readString(Paths.get("src/main/resources/XSD/IATA_AIDX_FlightLegRQ.xml"));

        camelContext.addRoutes(new BhsFlightlegRQ());
        camelContext.start();

        // Manually create the ProducerTemplate
        if (template == null) {
            template = camelContext.createProducerTemplate();
        }

        AdviceWith.adviceWith(camelContext, "bhsFlightLegRQProcessor", route -> {
            route.replaceFromWith("direct:bhs-flightlegrq-processor");
            route.interceptSendToEndpoint("{{route.flightLegRQEndpoint}}")
                    .skipSendToOriginalEndpoint()
                    .to("mock:flightLegRQEndpoint");
            route.mockEndpointsAndSkip("{{route.ackOutQueue}}");
            route.mockEndpoints("seda:save-log-into-db");
        });
    }


    @Test
    void SuccessScenarioTest() throws InterruptedException {
        // Mock endpoints
        MockEndpoint mockFlightLegNotifRQEndpoint = getMockEndpoint("mock:flightLegRQEndpoint");
        MockEndpoint mockAckOutQueue = getMockEndpoint("mock:{{route.ackOutQueue}}");
        MockEndpoint mockSaveLogDb = getMockEndpoint("mock:seda:save-log-into-db");

        // Expectations
        mockFlightLegNotifRQEndpoint.expectedMessageCount(1);
        mockAckOutQueue.expectedMessageCount(0);
        mockSaveLogDb.expectedMessageCount(1);

        // Send a test message with validationStatus as "SUCCESS"
        template.sendBodyAndHeader("direct:bhs-flightlegrq-processor", xmlFlightLeg, "validationStatus", "Success");

        // Assertions
        mockFlightLegNotifRQEndpoint.assertIsSatisfied();
        mockAckOutQueue.assertIsSatisfied();
        mockSaveLogDb.assertIsSatisfied();

        // Check if the ib_process_status And error_desc properties are set for success
        String processStatus = mockSaveLogDb.getExchanges().get(0).getProperty("ib_process_status", String.class);
        String errorDesc = mockSaveLogDb.getExchanges().get(0).getProperty("error_desc", String.class);
        assertEquals("Success", processStatus);
        assertEquals("",errorDesc);

    }

    @Test
    void FailureScenarioTest() throws InterruptedException {
        // Mock endpoints
        MockEndpoint mockFlightLegNotifRQEndpoint = getMockEndpoint("mock:flightLegRQEndpoint");
        MockEndpoint mockAckOutQueue = getMockEndpoint("mock:{{route.ackOutQueue}}");
        MockEndpoint mockSaveLogDb = getMockEndpoint("mock:seda:save-log-into-db");

        // Expectations
        mockFlightLegNotifRQEndpoint.expectedMessageCount(1);
        mockAckOutQueue.expectedMessageCount(1);
        mockSaveLogDb.expectedMessageCount(1);

        // Send a test message with validationStatus as "SUCCESS"
        template.sendBodyAndHeader("direct:bhs-flightlegrq-processor", xmlFlightLeg, "validationStatus", "Failure");

        // Assertions
        mockFlightLegNotifRQEndpoint.assertIsSatisfied();
        mockAckOutQueue.assertIsSatisfied();
        mockSaveLogDb.assertIsSatisfied();

        // Check if the ib_process_status And error_desc properties are set for success
        String processStatus = mockSaveLogDb.getExchanges().get(0).getProperty("ib_process_status", String.class);
        String errorDesc = mockSaveLogDb.getExchanges().get(0).getProperty("error_desc", String.class);
        assertEquals("Failure", processStatus);
        assertEquals("Message type is invalid",errorDesc);

    }

}