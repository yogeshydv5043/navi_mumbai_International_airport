package com.adani.routes;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.builder.RouteBuilder;

@ApplicationScoped
public class BhsFlightLegNotifRQ extends RouteBuilder {
    @Override
    public void configure() throws Exception {

        from("direct:bhs-flightlegnotifrq-processor")
                .routeId("bhsFlightLegNotifRQProcessor")
            .log("RouteId ${routeId}, and Hit received at ${date:now}")
            .convertBodyTo(String.class)
            .to("{{route.flightLegNotifRQEndpoint}}")
            .convertBodyTo(String.class)
                .choice()
                    .when(header("validationStatus").isEqualToIgnoreCase("SUCCESS"))
                        .log("Route id ${routeId}, Success Ack pushed into {{route.success_destination_subsystem}}")
                        .setProperty("ib_process_status", simple("{{route.success_ib_process_status}}"))
                        .setProperty("error_desc", simple(""))
                        .setProperty("destination_subsystem", simple("{{route.success_destination_subsystem}}, {{route.ack_destination_subsystem}}"))
                    .otherwise()
                        .log("Route id ${routeId}, Error Ack pushed into {{route.ack_destination_subsystem}}")
                        .setProperty("ib_process_status", simple("{{route.error_ib_process_status}}"))
                        .setProperty("error_desc", simple("Message type is invalid"))
                        .setProperty("destination_subsystem", simple("{{route.ack_destination_subsystem}}"))
                .end()
                .setProperty("final_response", simple("${body}", String.class))
                .to("{{route.ackOutQueue}}")
                .to("seda:save-log-into-db");

    }
}
