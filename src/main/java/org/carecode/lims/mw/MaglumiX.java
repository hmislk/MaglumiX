package org.carecode.lims.mw;

import com.google.gson.Gson;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import com.sun.net.httpserver.HttpServer;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.carecode.lims.libraries.*;

public class MaglumiX {

    public static final Logger logger = LogManager.getLogger(MaglumiX.class.toString());
    public static MiddlewareSettings middlewareSettings;
    public static LISCommunicator limsUtils;
    public static boolean testingLis = true;  // Indicates whether to run test before starting the server

    public static void main(String[] args) {
        logger.info("Maglumi started at: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        loadSettings();

        if (middlewareSettings != null && validateSettings(middlewareSettings)) {
            limsUtils = new LISCommunicator(logger, middlewareSettings);

            if (testingLis) {
                logger.info("Testing LIS started");
                testLis();  // Perform the test method before starting the server
                logger.info("Testing LIS Ended. System will now shutdown.");
                return;  // Instead of System.exit(), safely terminate the test phase
            }

            startServer();  // Start the server if no testing or after testing
        } else {
            logger.error("Failed to load or validate settings.");
        }
    }

    public static void testLis() {
        logger.info("Starting LIMS test process...");
        String filePath = "response.txt";  // Path to the test data file

        try {
            // Read the file content (which mimics the analyzer data)
            String hl7Message = new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8);

            // Log the HL7 message
            logger.info("Test HL7 message: " + hl7Message);

            // Process the HL7 message as if it were received directly from the analyzer
            ResponseHandler responseHandler = new ResponseHandler(logger, limsUtils);

            // Call the method to process the HL7 message (simulating a real analyzer request)
            responseHandler.processHL7Message(hl7Message, null);  // Passing 'null' as HttpExchange because it's not needed in this test context

            logger.info("Test HL7 message processed successfully and sent to LIMS.");

        } catch (IOException e) {
            logger.error("Failed to read test data from file: " + filePath, e);
        } catch (Exception e) {
            logger.error("An unexpected error occurred during the LIMS test process.", e);
        }
    }

    public static void loadSettings() {
        Gson gson = new Gson();
        try (FileReader reader = new FileReader("config.json")) {
            middlewareSettings = gson.fromJson(reader, MiddlewareSettings.class);
            logger.info("Settings loaded from config.json");
        } catch (IOException e) {
            logger.error("Failed to load settings from config.json", e);
        }
    }

    public static boolean validateSettings(MiddlewareSettings settings) {
        if (settings.getAnalyzerDetails() == null || settings.getLimsSettings() == null) {
            logger.error("Middleware settings validation failed: Missing analyzer or LIMS settings.");
            return false;
        }
        return true;
    }

    public static void startServer() {
        try {
            int port = middlewareSettings.getAnalyzerDetails().getHostPort();
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new ResponseHandler(logger, limsUtils));
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            logger.info("Server started on port " + port);

            // Add shutdown hook for graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                server.stop(0);
                logger.info("Server stopped gracefully.");
            }));
        } catch (IOException e) {
            logger.error("Failed to start the server", e);
        }
    }
}
