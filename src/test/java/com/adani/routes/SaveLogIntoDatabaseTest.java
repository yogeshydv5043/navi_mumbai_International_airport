package com.adani.routes;

import com.adani.entities.LogginEvent;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;

@QuarkusTest
class SaveLogIntoDatabaseTest extends CamelQuarkusTestSupport {

    @Inject
    private CamelContext camelContext;

    private String xmlLogInEvent;

    @BeforeEach
    void setup() throws Exception {
        xmlLogInEvent = Files.readString(Paths.get("src/main/resources/XSD/LoginEvent.xml"));
        // Add routes
        camelContext.addRoutes(createRouteBuilder());

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
        AdviceWith.adviceWith(camelContext, "SaveLogIntoDatabase", route -> {
            route.replaceFromWith("direct:save-log-into-db");
            route.mockEndpoints("mock:LogginEvent");
        });
    }

    //Create Dummy Route for test Cases
    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                onException(Exception.class)
                        .handled(true)
                        .log("Inside SaveLogIntoDatabase Exception block ${exception.message}")
                        .setBody(simple("<Ack><status>ERROR</status><message>${exception.message}</message></Ack>"))
                        .log("Exception : ${body}");

                from("seda:save-log-into-db").routeId("SaveLogIntoDatabase")
                        .log("Inside the SaveIntoDB")

                        .process(ex -> {
                            String transactionId = UUID.randomUUID().toString();
                            String error_desc = (ex.getProperty("error_desc") == null || ((String) ex.getProperty("error_desc")).isEmpty()) ? "" : ex.getProperty("error_desc", String.class);
                            String transaction_identifier = (ex.getProperty("transaction_identifier") == null || ((String) ex.getProperty("transaction_identifier")).isEmpty()) ? transactionId : ex.getProperty("transaction_identifier", String.class);
                            ex.getIn().setHeader("transaction_identifier", transaction_identifier);
                            System.out.print("transaction_identifier :: " + transaction_identifier);

                            LogginEvent logginEvent = new LogginEvent();

                            // Null check before setting properties
                            logginEvent.setLog_id(ex.getProperty("log_id") != null ? ex.getProperty("log_id").toString() : null);
                            logginEvent.setSource_subsystem(ex.getProperty("source_subsystem") != null ? ex.getProperty("source_subsystem").toString() : null);
                            logginEvent.setDestination_subsystem(ex.getProperty("destination_subsystem") != null ? ex.getProperty("destination_subsystem").toString() : null);

                            logginEvent.setTransaction_time(ex.getProperty("transaction_time") != null ? ex.getProperty("transaction_time").toString() : null);
                            logginEvent.setTransaction_identifier(transaction_identifier);
                            logginEvent.setIb_process_status(ex.getProperty("ib_process_status") != null ? ex.getProperty("ib_process_status").toString() : null);
                            logginEvent.setProcess_duration_ms(ex.getProperty("process_duration_ms") != null ? ex.getProperty("process_duration_ms").toString() : null);
                            logginEvent.setError_desc(error_desc);
                            logginEvent.setCreated_by(ex.getProperty("created_by") != null ? ex.getProperty("created_by").toString() : null);
                            logginEvent.setCreated_date(ex.getProperty("createdDate") != null ? ex.getProperty("createdDate").toString() : null);
                            logginEvent.setQueue_name(ex.getProperty("queue_name") != null ? ex.getProperty("queue_name").toString() : null);
                            ex.getIn().setBody(logginEvent);
                        })
                        .to("mock:LogginEvent")
                        .log(LoggingLevel.INFO, "Data insert into database")
                        .setBody(simple("${exchangeProperty.originalBody}"));
            }
        };
    }

    @Test
    void HealthCheckScenarioTest() throws InterruptedException {
        // Mock endpoints
        MockEndpoint mockSaveLog = getMockEndpoint("mock:LogginEvent");
        // Set expectations
        mockSaveLog.expectedMessageCount(1);

        // Send the test message
        template.sendBody("direct:save-log-into-db", xmlLogInEvent);

        // Assert the expectations
        mockSaveLog.assertIsSatisfied();
    }


}