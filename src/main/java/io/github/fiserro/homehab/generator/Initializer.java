package io.github.fiserro.homehab.generator;

import io.github.fiserro.homehab.HabState;
import io.github.fiserro.homehab.InputItem;
import io.github.fiserro.homehab.OutputItem;
import io.github.fiserro.homehab.ReadOnlyItem;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Initializes OpenHAB items with default values via REST API.
 */
@Slf4j
public class Initializer {

  private String openhabUrl;
  private HttpClient httpClient;

  public void initialize(GeneratorOptions options) throws Exception {
    this.openhabUrl = options.openhabUrl();
    this.httpClient = HttpClient.newHttpClient();

    log.info("Initializing all items with default values...");
    int inputCount = initializeInputItems();
    int outputCount = initializeOutputItems();
    int readOnlyCount = initializeReadOnlyItems();
    log.info("Initialized {} input, {} output, {} read-only items", inputCount, outputCount, readOnlyCount);
  }

  public int initializeInputItems() throws Exception {
    log.info("Initializing input items (only if NULL/UNDEF)...");
    int count = 0;
    HabState defaultState = HabState.builder().build();

    for (Field field : HabState.class.getDeclaredFields()) {
      if (field.isAnnotationPresent(InputItem.class)) {
        field.setAccessible(true);
        Object value = field.get(defaultState);
        String itemName = field.getName();
        String defaultValue = formatState(value);

        String currentState = getItemState(itemName);
        if (needsInitialization(currentState)) {
          if (updateItemState(itemName, defaultValue)) {
            log.info("  ✓ {}: {} (was {})", itemName, defaultValue, currentState);
            count++;
          } else {
            log.warn("  ✗ {}: failed", itemName);
          }
        } else {
          log.debug("  - {}: keeping current value {}", itemName, currentState);
        }
      }
    }
    return count;
  }

  public int initializeOutputItems() throws Exception {
    log.info("Initializing output items (only if NULL/UNDEF)...");
    int count = 0;
    HabState defaultState = HabState.builder().build();

    for (Field field : HabState.class.getDeclaredFields()) {
      if (field.isAnnotationPresent(OutputItem.class)) {
        field.setAccessible(true);
        Object value = field.get(defaultState);
        String itemName = field.getName();
        String defaultValue = formatState(value);

        String currentState = getItemState(itemName);
        if (needsInitialization(currentState)) {
          if (updateItemState(itemName, defaultValue)) {
            log.info("  ✓ {}: {} (was {})", itemName, defaultValue, currentState);
            count++;
          } else {
            log.warn("  ✗ {}: failed", itemName);
          }
        } else {
          log.debug("  - {}: keeping current value {}", itemName, currentState);
        }
      }
    }
    return count;
  }

  public int initializeReadOnlyItems() throws Exception {
    log.info("Initializing read-only items (only if NULL/UNDEF)...");
    int count = 0;
    HabState defaultState = HabState.builder().build();

    for (Field field : HabState.class.getDeclaredFields()) {
      if (field.isAnnotationPresent(ReadOnlyItem.class)) {
        field.setAccessible(true);
        Object value = field.get(defaultState);
        String itemName = field.getName();
        String defaultValue = formatState(value);

        String currentState = getItemState(itemName);
        if (needsInitialization(currentState)) {
          if (updateItemState(itemName, defaultValue)) {
            log.info("  ✓ {}: {} (was {})", itemName, defaultValue, currentState);
            count++;
          } else {
            log.warn("  ✗ {}: failed", itemName);
          }
        } else {
          log.debug("  - {}: keeping current value {}", itemName, currentState);
        }
      }
    }
    return count;
  }

  private boolean needsInitialization(String currentState) {
    return currentState == null || "NULL".equals(currentState) || "UNDEF".equals(currentState);
  }

  private String getItemState(String itemName) {
    try {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(openhabUrl + "/rest/items/" + itemName + "/state"))
          .header("Accept", "text/plain")
          .GET()
          .build();

      HttpResponse<String> response = httpClient.send(request,
          HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        return response.body();
      }
      return null;
    } catch (Exception e) {
      log.debug("Failed to get state for '{}': {}", itemName, e.getMessage());
      return null;
    }
  }

  private boolean updateItemState(String itemName, String state) {
    try {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(openhabUrl + "/rest/items/" + itemName + "/state"))
          .header("Content-Type", "text/plain")
          .header("Accept", "application/json")
          .PUT(HttpRequest.BodyPublishers.ofString(state))
          .build();

      HttpResponse<String> response = httpClient.send(request,
          HttpResponse.BodyHandlers.ofString());

      int statusCode = response.statusCode();
      if (statusCode >= 200 && statusCode < 300) {
        return true;
      } else if (statusCode == 404) {
        log.debug("Item '{}' not found in OpenHAB - restart OpenHAB to load new items", itemName);
      } else {
        log.debug("Failed to update '{}': HTTP {} - {}", itemName, statusCode, response.body());
      }
      return false;
    } catch (java.net.ConnectException e) {
      log.warn("Cannot connect to OpenHAB at {} - is it running?", openhabUrl);
      return false;
    } catch (Exception e) {
      log.debug("Failed to update '{}': {}", itemName, e.getMessage());
      return false;
    }
  }

  private String formatState(Object value) {
    if (value instanceof Boolean) {
      return ((Boolean) value) ? "ON" : "OFF";
    }
    return String.valueOf(value);
  }
}
