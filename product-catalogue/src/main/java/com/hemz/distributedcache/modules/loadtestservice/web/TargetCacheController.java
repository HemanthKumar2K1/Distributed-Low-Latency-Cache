package com.hemz.distributedcache.modules.loadtestservice.web;

import com.hemz.distributedcache.modules.loadtestservice.run.RunConflictException;
import com.hemz.distributedcache.modules.loadtestservice.run.RunService;
import com.hemz.distributedcache.modules.loadtestservice.target.TargetCacheClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * GET /api/target/cache                     is Product Catalogue's cache on?
 * PUT /api/target/cache {"enabled": bool}   switch it. 409 while a run is active.
 * POST /api/target/cache/nodes/{name}/kill  stop one cache node (failure test). Allowed DURING a run:
 *                                           killing a node under load is the whole point of the test.
 */
@RestController
@RequestMapping("/api/target/cache")
public class TargetCacheController {

    private static final Pattern NODE_NAME = Pattern.compile("node-\\d{1,3}");

    private final TargetCacheClient cache;
    private final RunService runs;

    public TargetCacheController(TargetCacheClient cache, RunService runs) {
        this.cache = cache;
        this.runs = runs;
    }

    public record EnabledRequest(@NotNull Boolean enabled) {
    }

    @GetMapping
    public Map<String, Object> status() {
        return cache.status();
    }

    @PutMapping
    public Map<String, Object> setEnabled(@Valid @RequestBody EnabledRequest body) {
        runs.active().ifPresent(r -> {
            throw new RunConflictException("Run " + r.id() + " is running. Switch the cache after it finishes, "
                    + "so one run measures one setup.");
        });
        return cache.setEnabled(body.enabled());
    }

    @PostMapping("/nodes/{name}/kill")
    public ResponseEntity<String> killNode(@PathVariable String name)
            throws IOException, InterruptedException {
        if (!NODE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Node names look like node-1, got: " + name);
        }
        HttpResponse<String> res = cache.killNode(name);
        MediaType type = res.statusCode() == 200 ? MediaType.APPLICATION_JSON : MediaType.APPLICATION_PROBLEM_JSON;
        return ResponseEntity.status(res.statusCode()).contentType(type).body(res.body());
    }
}
