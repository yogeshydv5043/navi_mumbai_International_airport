package com.adani.routes;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@QuarkusTest
class BhsContextRoutesTest extends CamelQuarkusTestSupport {

    private String flightLegRs;
    private String flightLegNotifRQ;

    @Inject
    CamelContext camelContext;

    @BeforeEach
    public void setup() throws Exception {
        // Use consistent naming and add error handling for file reading
        try {
            flightLegRs = Files.readString(Paths.get("src/main/resources/XSD/IATA_AIDX_FlightLegRS.xml"));
            flightLegNotifRQ = Files.readString(Paths.get("src/main/resources/XSD/IATA_AIDX_FlightLegNotifRQ.xml"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read XML files", e);
        }

        // Clear existing routes to prevent conflicts
//        camelContext.removeRoute(camelContext.getRouteDefinitions());

        camelContext.start();
        // Adding a route builder directly
        camelContext.addRoutes(createRouteBuilder());


        // Using advice to mock endpoints
        AdviceWith.adviceWith(camelContext, "bhsFlightLegNotifRQHttpRoute", route -> {
            route.mockEndpointsAndSkip("{{route.ackOutQueue}}");
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                rest("/api/v1/bhsibadapter")
                        .post("/bhs-flightlegrs")
                        .to("mock:direct:bhs-flightlegrs-route") // mocking
                        .post("/bhs-flightLegNotifRQ")
                        .to("direct:bhs-flightlegnotifrq-route");

                from("direct:bhs-flightlegnotifrq-route")
                        .routeId("bhsFlightLegNotifRQHttpRoute")
                        .log("RouteId ${routeId} and, Hit received at ${date:now}")
                        .convertBodyTo(String.class)
                        .setExchangePattern(ExchangePattern.InOnly)
                        .to("{{route.ackOutQueue}}") // mocking
                        .log("Route id ${routeId}, Message pushed into BHS-OUT-QUEUE");
            }
        };
    }

    @Test
    void bhsFlightLegRSTest() throws InterruptedException {
        MockEndpoint mockFlightLegRS = camelContext.getEndpoint("mock:direct:bhs-flightlegrs-route", MockEndpoint.class);
        mockFlightLegRS.expectedMessageCount(1);

        RestAssured.given()
                .contentType(ContentType.XML)
                .body(flightLegRs)
                .when()
                .post("/api/v1/bhsibadapter/bhs-flightlegrs")
                .then()
                .statusCode(200);

        mockFlightLegRS.assertIsSatisfied();
    }

    @Test
    void bhsFlightLegNotifRQTest() throws InterruptedException {
        // Mock endpoint to verify message receipt
        MockEndpoint mockAckOutQueue = getMockEndpoint("mock:{{route.ackOutQueue}}");
        mockAckOutQueue.expectedMessageCount(1);

        // Send XML payload to REST endpoint using RestAssured
        RestAssured.given()
                .contentType(ContentType.XML)
                .body(flightLegNotifRQ)
                .when()
                .post("/api/v1/bhsibadapter/bhs-flightLegNotifRQ")
                .then()
                .statusCode(200);

        // Verify that the message was received by mock endpoint
        mockAckOutQueue.assertIsSatisfied();
    }
}