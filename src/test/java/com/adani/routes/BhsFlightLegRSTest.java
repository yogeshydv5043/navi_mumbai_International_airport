package com.adani.routes;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class BhsFlightLegRSTest  extends CamelQuarkusTestSupport {

    private String xmlFlightLeg;

    @Inject
    private CamelContext camelContext;

    @BeforeEach
    public void setUp() throws Exception {
        xmlFlightLeg = Files.readString(Paths.get("src/main/resources/XSD/IATA_AIDX_FlightLegNotifRQ.xml"));

        camelContext.addRoutes(new BhsFlightLegRS());
        camelContext.start();

        // Manually create the ProducerTemplate
        if (template == null) {
            template = camelContext.createProducerTemplate();
        }

        AdviceWith.adviceWith(camelContext, "bhsFlightLegRSRoute", route -> {
            route.replaceFromWith("direct:bhs-flightlegrs-route");
            route.mockEndpointsAndSkip("{{route.ackOutQueue}}");

        });
    }

    @Test
    void bhsFlightLegRQTest() throws InterruptedException {
        // Mock endpoints
        MockEndpoint mockAckOutQueue = getMockEndpoint("mock:{{route.ackOutQueue}}");

        // Expectations
        mockAckOutQueue.expectedMessageCount(1);

        // Send a test message with validationStatus as "SUCCESS"
        template.sendBody("direct:bhs-flightlegrs-route", xmlFlightLeg);

        // Assertions
        mockAckOutQueue.assertIsSatisfied();

    }
}