package com.adani.routes;


import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.support.builder.Namespaces;
import org.xml.sax.SAXException;

import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.StartElement;
import java.io.Writer;

@ApplicationScoped
public class BhsIbAdapterInQueue extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("{{route.ibInQueue}}").routeId("BHSAdapterInQueue")
                .log("RouteId ${routeId} and Hit recieved at ${date:now:yyyy-MM-dd HH:mm:ss.SSS}")
                .setHeader("subsystem", simple("{{route.source_subsystem}}"))
                .setProperty("originalBody", simple("${body}"))
                .setHeader("log_id", simple("${date:now:yyyyMMddHHmmss}"))
                .setProperty("log_id", simple("${header.log_id}"))
                .setProperty("source_subsystem", simple("{{route.source_subsystem}}"))
                .setProperty("destination_subsystem", simple(""))
                .setProperty("message_type", simple(""))
                .setProperty("original_request", simple("${body}", String.class))
                .setProperty("final_response", simple(""))
                .setProperty("transaction_time", simple("${date:now:yyyy-MM-dd HH:mm:ss.SSS}"))
                .setProperty("ib_process_status", simple("Pending"))
                .setProperty("queue_name", simple("{{route.source_subsystem}}"))
                .setProperty("process_duration_ms", simple(""))
                .setProperty("error_desc", simple(""))
                .setProperty("created_by", simple("{{route.createBy}}"))
                .setProperty("createdDate", simple("${date:now:yyyy-MM-dd HH:mm:ss.SSS}"))
                // Validate the xml request and extract value of TransactionIdentifier
                .process("XMLValidationProcessor")
                // Validate message type and send to their respective validate handler service
                .to("direct:bhs-request-processor")
                .log("BhsAdapterInQueue_002 :: Message processed");

        from("direct:bhs-request-processor").routeId("BhsRequestProcessor")
                .log(LoggingLevel.INFO, "RouteId ${routeId}, and Message type : ${header.messageType}")

                .choice()
                .when(bodyAs(String.class).contains("HealthCheckRequest"))
                    .setProperty("message_type",simple("HealthCheck"))
                    .to("seda:save-log-into-db")
                    .to("direct:bhs-healthcheck-processor")
                    .log("RouteId ${routeId}, and HealthCheck processed successfully")
                .when(bodyAs(String.class).contains("IATA_AIDX_FlightLegRQ"))
                    .setProperty("message_type", simple("FlightLegRQ"))
                    .to("seda:save-log-into-db")
                    .to("direct:bhs-flightlegrq-processor")
                    .log("RouteId ${routeId}, and FlightLegRQ Message processed to with messageType: ${header.message_type}")
                .when(bodyAs(String.class).contains("IATA_AIDX_FlightLegNotifRQ"))
                    .setProperty("message_type",simple("FlightLegNotifRQ"))
                    .to("seda:save-log-into-db")
                    .to("direct:bhs-flightlegnotifrq-processor")
                    .log("RouteId ${routeId}, and FlightLegNotifRQ Message processed to with messageType: ${header.message_type}")
                .otherwise()
                    .to("direct:bhs-flightlegrq-processor")
                    .log("RouteId ${routeId}, and Something went wrong with messageType: ${header.message_type}")
                .end();
     }
    }

