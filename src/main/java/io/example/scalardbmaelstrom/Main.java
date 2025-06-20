package io.example.scalardbmaelstrom;

import io.example.scalardbmaelstrom.kv.KvStorageService;
import io.example.scalardbmaelstrom.protocol.MaelstromRequest;
import io.example.scalardbmaelstrom.protocol.MaelstromResponse;
import io.example.scalardbmaelstrom.util.JsonUtil;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
  private static final Logger logger = LoggerFactory.getLogger(Main.class);
  private static final int ERROR_KEY_NOT_FOUND = 20;
  private static final int ERROR_CAS_FAILED = 22;

  private static String nodeId;
  private static KvStorageService kvService;

  public static void main(String[] args) {
    String configPath = "database.properties";

    for (int i = 0; i < args.length - 1; i++) {
      if ("--config".equals(args[i])) {
        configPath = args[i + 1];
        break;
      }
    }

    try {
      // Add debug logging to file
      java.io.FileWriter debugLog = new java.io.FileWriter("/tmp/maelstrom-debug.log", true);
      debugLog.write("=== APPLICATION STARTED ===\n");
      debugLog.write("Working directory: " + System.getProperty("user.dir") + "\n");
      debugLog.write("Config path: " + configPath + "\n");
      debugLog.flush();

      BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
      String line;

      debugLog.write("Starting to read input...\n");
      debugLog.flush();

      while ((line = reader.readLine()) != null) {
        try {
          debugLog.write("RECEIVED: " + line + "\n");
          debugLog.flush();

          MaelstromRequest request = JsonUtil.fromJson(line, MaelstromRequest.class);
          debugLog.write(
              "PARSED: type=" + request.getType() + ", nodeId=" + request.getNodeId() + "\n");
          debugLog.flush();

          MaelstromResponse response = handleRequest(request, configPath);
          String responseJson = JsonUtil.toJson(response);

          debugLog.write("SENDING: " + responseJson + "\n");
          debugLog.flush();

          System.out.println(responseJson);
          System.out.flush();
        } catch (Exception e) {
          debugLog.write("ERROR: " + e.getMessage() + "\n");
          debugLog.flush();
          logger.error("Error processing request: " + line, e);
        }
      }

      debugLog.write("=== INPUT STREAM ENDED ===\n");
      debugLog.close();

    } catch (Exception e) {
      logger.error("Fatal error", e);
      System.exit(1);
    } finally {
      try {
        if (kvService != null) {
          kvService.close();
        }
      } catch (Exception e) {
        logger.error("Error closing kvService", e);
      }
    }
  }

  private static MaelstromResponse handleRequest(MaelstromRequest request, String configPath)
      throws Exception {
    String type = request.getType();
    Integer msgId = request.getMsgId();

    switch (type) {
      case "init":
        nodeId = request.getNodeId();
        // Initialize kvService during init to avoid delays later
        if (kvService == null) {
          kvService = new KvStorageService(configPath);
        }
        return MaelstromResponse.initOk(nodeId, request.getSrc(), msgId);

      case "read":
        if (kvService == null) {
          kvService = new KvStorageService(configPath);
        }
        String readKey = request.getKey();
        Optional<String> value = kvService.read(readKey);
        if (value.isPresent()) {
          // Parse JSON back to original type
          Object originalValue = JsonUtil.fromJson(value.get(), Object.class);
          return MaelstromResponse.readOk(nodeId, request.getSrc(), msgId, originalValue);
        } else {
          return MaelstromResponse.fail(
              nodeId, request.getSrc(), msgId, ERROR_KEY_NOT_FOUND, "key not found");
        }

      case "write":
        if (kvService == null) {
          kvService = new KvStorageService(configPath);
        }
        String writeKey = request.getKey();
        // Store value as JSON to preserve type
        String writeValue = JsonUtil.toJson(request.getValue());
        kvService.write(writeKey, writeValue);
        return MaelstromResponse.writeOk(nodeId, request.getSrc(), msgId);

      case "cas":
        if (kvService == null) {
          kvService = new KvStorageService(configPath);
        }
        String casKey = request.getKey();
        // Store values as JSON to preserve type
        String from = request.getFrom() != null ? JsonUtil.toJson(request.getFrom()) : null;
        String to = JsonUtil.toJson(request.getTo());

        boolean success = kvService.cas(casKey, from, to);
        if (success) {
          return MaelstromResponse.casOk(nodeId, request.getSrc(), msgId);
        } else {
          return MaelstromResponse.fail(
              nodeId, request.getSrc(), msgId, ERROR_CAS_FAILED, "expected value does not match");
        }

      default:
        return MaelstromResponse.fail(
            nodeId, request.getSrc(), msgId, 10, "unsupported operation: " + type);
    }
  }
}