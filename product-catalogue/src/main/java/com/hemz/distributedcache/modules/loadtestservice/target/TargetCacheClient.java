package com.hemz.distributedcache.modules.loadtestservice.target;

import com.hemz.distributedcache.modules.loadtestservice.config.LoadTestProperties;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Talks to Product Catalogue's /api/cache endpoints over HTTP, like any outside client.
 * The load tester treats Product Catalogue as a black box, so it measures exactly what users see.
 */
@Component
public class TargetCacheClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(2);
    private static final TypeReference<Map<String, Object>> STATUS_TYPE = new TypeReference<>() {
    };

    private final String target;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    public TargetCacheClient(LoadTestProperties props, ObjectMapper mapper) {
        this.target = props.target();
        this.mapper = mapper;
    }

    public Map<String, Object> status() {
        return call(HttpRequest.newBuilder(uri("/api/cache/stats")).GET());
    }

    public Map<String, Object> setEnabled(boolean enabled) {
        return call(HttpRequest.newBuilder(uri("/api/cache/enabled"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{\"enabled\":" + enabled + "}")));
    }

    /**
     * Asks Product Catalogue to stop one cache node (failure test). Passes Product Catalogue's answer
     * through as it is (status and JSON body), so its error message reaches the dashboard.
     */
    public HttpResponse<String> killNode(String nodeName) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/cache/nodes/" + nodeName + "/shutdown"))
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Sends the request and passes Product Catalogue's JSON through as it is, plus "available": true.
     * Never throws for a network problem: then "available" is false and "reason" says why.
     */
    private Map<String, Object> call(HttpRequest.Builder request) {
        HttpResponse<String> res;
        try {
            res = client.send(request.timeout(REQUEST_TIMEOUT).build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            return unavailable("cannot reach Product Catalogue: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return unavailable("interrupted");
        }
        if (res.statusCode() != 200) {
            return unavailable(res.statusCode() == 404
                    ? "Product Catalogue has no /api/cache endpoints (is this Product Catalogue?)"
                    : "Product Catalogue returned HTTP " + res.statusCode());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("available", true);
        out.putAll(mapper.readValue(res.body(), STATUS_TYPE));
        return out;
    }

    /** The answer when Product Catalogue could not give a status. */
    private static Map<String, Object> unavailable(String reason) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("available", false);
        out.put("reason", reason);
        return out;
    }

    private URI uri(String path) {
        return URI.create(target + path);
    }
}
