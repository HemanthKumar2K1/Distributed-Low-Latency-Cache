package com.hemz.distributedcache.modules.loadtestservice.metrics;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Reads Product Catalogue's /actuator/prometheus. */
public final class ServerMetricsScraper {

    /** Both under the 1 s scrape interval, so a slow scrape never overlaps the next one. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofMillis(800);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMillis(900);

    private final HttpClient client;
    private final URI endpoint;
    private final String poolName;

    public ServerMetricsScraper(String targetBaseUrl, String poolName) {
        String base = targetBaseUrl.endsWith("/") ? targetBaseUrl.substring(0, targetBaseUrl.length() - 1) : targetBaseUrl;
        this.endpoint = URI.create(base + "/actuator/prometheus");
        this.poolName = poolName;
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    public ServerSnapshot scrape() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "text/plain;version=0.0.4")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("GET " + endpoint + " returned HTTP " + response.statusCode());
        }
        return ServerSnapshot.from(PrometheusText.parse(response.body()), poolName, System.nanoTime());
    }

    public URI endpoint() {
        return endpoint;
    }
}
