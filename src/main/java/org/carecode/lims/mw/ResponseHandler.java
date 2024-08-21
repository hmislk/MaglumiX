package org.carecode.lims.mw;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.apache.logging.log4j.Logger;
import org.carecode.lims.libraries.*;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import ca.uhn.hl7v2.model.v251.message.*;
import ca.uhn.hl7v2.model.v251.segment.*;
import ca.uhn.hl7v2.parser.*;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;

public class ResponseHandler implements HttpHandler {

    private final Logger logger;
    private final LISCommunicator limsUtils;

    public ResponseHandler(Logger logger, LISCommunicator limsUtils) {
        this.logger = logger;
        this.limsUtils = limsUtils;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        logger.info("Received " + method + " request from " + exchange.getRemoteAddress());

        if ("POST".equalsIgnoreCase(method)) {
            StringBuilder requestBody = new StringBuilder();
            try (InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)) {
                int data;
                while ((data = isr.read()) != -1) {
                    requestBody.append((char) data);
                }
            }

            String hl7Message = requestBody.toString();
            logger.info("Received HL7 message: " + hl7Message);

            try {
                processHL7Message(hl7Message, exchange);
            } catch (HL7Exception e) {
                logger.error("Failed to process HL7 message", e);
                exchange.sendResponseHeaders(500, -1); // 500 Internal Server Error
            }
        } else {
            exchange.sendResponseHeaders(405, -1); // 405 Method Not Allowed
        }
    }

    public void processHL7Message(String hl7Message, HttpExchange exchange) throws HL7Exception, IOException {
        // Parse the HL7 message
        PipeParser parser = new PipeParser();
        Message message = parser.parse(hl7Message);

        if (message instanceof OML_O33) { // Handle order messages
            handleOrderMessage((OML_O33) message, exchange);
        } else if (message instanceof ORU_R01) { // Handle result messages
            handleResultMessage((ORU_R01) message, exchange);
        } else {
            logger.warn("Unsupported message type received");
            exchange.sendResponseHeaders(400, -1); // 400 Bad Request
        }
    }

    private void handleOrderMessage(OML_O33 orderMessage, HttpExchange exchange) throws IOException {
        // Extract information from the order message
        PID pid = orderMessage.getPATIENT().getPID();
        String patientId = pid.getPatientIdentifierList(0).getIDNumber().getValue();
        logger.info("Processing order for patient: " + patientId);

        // Send a response back to the analyzer
        String responseMessage = "Order received and processed";
        exchange.sendResponseHeaders(200, responseMessage.getBytes(StandardCharsets.UTF_8).length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseMessage.getBytes(StandardCharsets.UTF_8));
        }

        // Perform any necessary actions based on the order (e.g., sending to LIS)
        // For now, just log the action
        logger.info("Order for patient " + patientId + " processed successfully.");
    }

    private void handleResultMessage(ORU_R01 resultMessage, HttpExchange exchange) throws IOException {
        // Extract information from the result message
        PID pid = resultMessage.getPATIENT_RESULT().getPATIENT().getPID();
        String patientId = pid.getPatientIdentifierList(0).getIDNumber().getValue();
        logger.info("Processing result for patient: " + patientId);

        // Send a response back to the analyzer
        String responseMessage = "Results received and processed";
        exchange.sendResponseHeaders(200, responseMessage.getBytes(StandardCharsets.UTF_8).length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseMessage.getBytes(StandardCharsets.UTF_8));
        }

        // Perform any necessary actions based on the result (e.g., sending to LIS)
        // For now, just log the action
        logger.info("Result for patient " + patientId + " processed successfully.");
    }
}
