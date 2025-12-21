package io.github.fiserro.homehab.generator;

import io.github.fiserro.homehab.InputItem;
import io.github.fiserro.homehab.OutputItem;
import io.github.fiserro.homehab.ReadOnlyItem;
import io.github.fiserro.homehab.module.CommonModule;
import io.github.fiserro.homehab.module.FlowerModule;
import io.github.fiserro.homehab.module.HrvModule;
import io.github.fiserro.options.Option;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Set;
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
        Set<String> processed = new HashSet<>();

        int inputCount = initializeItems(InputItem.class, processed);
        int outputCount = initializeItems(OutputItem.class, processed);
        int readOnlyCount = initializeItems(ReadOnlyItem.class, processed);

        log.info("Initialized {} input, {} output, {} read-only items", inputCount, outputCount, readOnlyCount);
    }

    private int initializeItems(Class<? extends Annotation> annotationType, Set<String> processed)
            throws Exception {
        log.info("Initializing {} items (only if NULL/UNDEF)...", annotationType.getSimpleName());
        int count = 0;

        // Process all module interfaces
        count += initializeModuleItems(CommonModule.class, annotationType, processed);
        count += initializeModuleItems(HrvModule.class, annotationType, processed);
        count += initializeModuleItems(FlowerModule.class, annotationType, processed);

        return count;
    }

    private int initializeModuleItems(
            Class<?> moduleClass,
            Class<? extends Annotation> annotationType,
            Set<String> processed) {
        int count = 0;

        for (Method method : moduleClass.getDeclaredMethods()) {
            // Skip non-option methods
            if (!method.isAnnotationPresent(Option.class)) {
                continue;
            }

            String itemName = method.getName();

            // Skip if already processed
            if (processed.contains(itemName)) {
                continue;
            }

            // Check if has the required annotation
            if (!method.isAnnotationPresent(annotationType)) {
                continue;
            }

            // Get default value from default method
            Object value = getDefaultValue(method);
            String defaultValue = formatState(value);

            String currentState = getItemState(itemName);
            if (needsInitialization(currentState)) {
                if (updateItemState(itemName, defaultValue)) {
                    log.info("  + {}: {} (was {})", itemName, defaultValue, currentState);
                    count++;
                } else {
                    log.warn("  x {}: failed", itemName);
                }
            } else {
                log.debug("  - {}: keeping current value {}", itemName, currentState);
            }

            processed.add(itemName);
        }

        return count;
    }

    private Object getDefaultValue(Method method) {
        // Default methods have default implementations we can read
        if (method.isDefault()) {
            try {
                // For primitives, return appropriate defaults based on return type
                Class<?> returnType = method.getReturnType();
                if (returnType == boolean.class) {
                    return false;
                } else if (returnType == int.class) {
                    return 0;
                } else if (returnType == long.class) {
                    return 0L;
                } else if (returnType == double.class) {
                    return 0.0;
                }
            } catch (Exception e) {
                log.debug("Could not get default value for {}: {}", method.getName(), e.getMessage());
            }
        }
        return null;
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
