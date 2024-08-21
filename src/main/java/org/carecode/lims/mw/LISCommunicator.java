package org.carecode.lims.mw;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.Logger;
import org.carecode.lims.libraries.ResultsRecord;
import org.carecode.lims.libraries.PatientRecord;
import org.carecode.lims.libraries.OrderRecord;
import org.carecode.lims.libraries.MiddlewareSettings;
import org.carecode.lims.libraries.DataBundle;

public class LISCommunicator {

    private final Logger logger;
    private static final Gson gson = new Gson();
    private final MiddlewareSettings middlewareSettings;

    public LISCommunicator(Logger logger, MiddlewareSettings settings) {
        this.logger = logger;
        this.middlewareSettings = settings;
    }

    // This method creates a DataBundle from a patient record and a list of orders or results
    public DataBundle createDataBundle(PatientRecord patientRecord, List<OrderRecord> orders, List<ResultsRecord> results) {
        DataBundle dataBundle = new DataBundle();
        dataBundle.setMiddlewareSettings(middlewareSettings);
        dataBundle.setPatientRecord(patientRecord);
        
        // Add order records if present
        if (orders != null) {
            dataBundle.getOrderRecords().addAll(orders);
            logger.info("Order records added: " + orders.size());
        }
        
        // Add results records if present
        if (results != null) {
            dataBundle.getResultsRecords().addAll(results);
            logger.info("Results records added: " + results.size());
        }

        logger.info("DataBundle created for patient ID: " + patientRecord.getPatientId());
        return dataBundle;
    }

    // This method sends data (either order requests or test results) to the LIS
    public void sendToLIS(DataBundle dataBundle) {
        try {
            String endpointUrl = middlewareSettings.getLimsSettings().getLimsServerBaseUrl() + "/send_data";
            URL url = new URL(endpointUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            // Serialize the DataBundle to JSON
            String jsonInputString = gson.toJson(dataBundle);
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            // Log the response from the server
            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                logger.info("Response from LIS: " + response.toString());
            }

        } catch (Exception e) {
            logger.error("Failed to send data to LIS", e);
        }
    }

    // Example method to create a patient record
    public PatientRecord createPatientRecord(String patientId, String patientName, String patientSex, String dob, String attendingDoctor) {
        return new PatientRecord(0, patientId, null, patientName, null, patientSex, null, dob, null, null, attendingDoctor);
    }

    // Example method to create a result record
    public ResultsRecord createResultsRecord(String testCode, double resultValue, String resultUnits, String sampleId) {
        return new ResultsRecord(
                0, // frameNumber
                testCode, // Test Code
                resultValue, // Result Value
                0, // Minimum Value
                0, // Maximum Value
                "", // Flag
                "", // Sample Type
                resultUnits, // Result Units
                null, // Result DateTime
                middlewareSettings.getAnalyzerDetails().getAnalyzerName(), // Instrument Name
                sampleId // Sample ID
        );
    }

    // Example method to create an order record
    public OrderRecord createOrderRecord(String sampleId, List<String> testNames, String specimenCode, String orderDateTimeStr) {
        return new OrderRecord(0, sampleId, testNames, specimenCode, orderDateTimeStr, "");
    }
}
