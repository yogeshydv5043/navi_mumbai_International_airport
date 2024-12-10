package com.adani.routes;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;

@ApplicationScoped
public class BhsContextRoutes extends RouteBuilder {
    @Override
    public void configure() throws Exception {

        rest("/api/v1/bhsibadapter")
                .post("/bhs-flightlegrs")
                    .to("direct:bhs-flightlegrs-route")
                    .post("/bhs-flightLegNotifRQ")
                    .to("direct:bhs-flightlegnotifrq-route");
    
            from("direct:bhs-flightlegnotifrq-route")
                    .routeId("bhsFlightLegNotifRQHttpRoute")
                    .log("RouteId ${routeId} and, Hit received at ${date:now}")
                    .convertBodyTo(String.class)
                    .setExchangePattern(ExchangePattern.InOnly)
                    .to("{{route.ackOutQueue}}")
                    .log("Route id ${routeId}, Message pushed into BHS-OUT-QUEUE");

    }
}
