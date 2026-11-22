package com.hemz.distributedcache.modules.productservice.cache;

import com.hemz.distributedcache.modules.cacheclient.CacheAdmin;
import com.hemz.distributedcache.modules.cacheclient.CacheNodeStatus;
import com.hemz.distributedcache.modules.cacheclient.remote.CacheServerError;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GET    /api/cache/stats     on/off, what Cache System holds, hit rate since startup
 * PUT    /api/cache/enabled   {"enabled": true|false}  switch without a restart (also empties it)
 * DELETE /api/cache           empty it
 * POST   /api/cache/nodes/{name}/shutdown   stop one node on purpose (failure test)
 */
@RestController
@RequestMapping("/api/cache")
public class CacheAdminController {

    private final CacheAdmin admin;
    private final MicrometerCacheMetrics metrics;
    private final CacheSwitch cacheSwitch;

    public CacheAdminController(CacheAdmin admin, MicrometerCacheMetrics metrics, CacheSwitch cacheSwitch) {
        this.admin = admin;
        this.metrics = metrics;
        this.cacheSwitch = cacheSwitch;
    }

    public record EnabledRequest(@NotNull Boolean enabled) {
    }

    /**
     * entries and valueBytes add up the nodes that answered; null when none did.
     * cacheServerUp is true only when every node answered. nodes lists each one.
     */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        List<CacheNodeStatus> nodes = admin.nodes();
        List<CacheNodeStatus> up = nodes.stream().filter(CacheNodeStatus::up).toList();
        long hits = metrics.hits();
        long misses = metrics.misses();
        long lookups = hits + misses;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", cacheSwitch.isEnabled());
        out.put("entries", up.isEmpty() ? null : CacheNodeStatus.totalKeys(up));
        out.put("valueBytes", CacheNodeStatus.sum(up, CacheNodeStatus::valueBytes).orElse(null));
        out.put("hits", hits);
        out.put("misses", misses);
        out.put("errors", metrics.errors());
        out.put("hitRatePct", lookups == 0 ? null : Math.round(hits * 1000.0 / lookups) / 10.0);
        out.put("cacheServer", admin.address());
        out.put("cacheServerUp", up.size() == nodes.size());
        out.put("nodes", nodes);
        // Which cache this is ("cache-system ×3", "redis ×1") and the memory its processes use.
        out.put("backend", backend(nodes));
        out.put("usedMemory", CacheNodeStatus.sum(up, CacheNodeStatus::usedMemory).orElse(null));
        return out;
    }

    @PutMapping("/enabled")
    public Map<String, Object> setEnabled(@Valid @RequestBody EnabledRequest body) {
        cacheSwitch.set(body.enabled());
        return stats();
    }

    @DeleteMapping
    public ResponseEntity<Map<String, String>> clear() {
        try {
            admin.flushAll();
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Cache System at " + admin.address() + " could not be emptied: " + e.getMessage()));
        }
    }

    /**
     * Sends SHUTDOWN to one node. The node must have been started with --allow-shutdown=true
     * (start-nodes.sh does that); otherwise it refuses and this returns 409. Bring it back with
     * cache-system/start-nodes.sh <port>.
     */
    @PostMapping("/nodes/{name}/shutdown")
    public ResponseEntity<?> shutdownNode(@PathVariable String name) {
        try {
            admin.shutdownNode(name);
            return ResponseEntity.ok(stats());
        } catch (IllegalArgumentException e) {
            return problem(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (CacheServerError e) {
            return problem(HttpStatus.CONFLICT, name + " refused: " + e.getMessage());
        } catch (RuntimeException e) {
            return problem(HttpStatus.SERVICE_UNAVAILABLE, name + " could not be reached: " + e.getMessage());
        }
    }

    /** "cache-system ×3" or "redis ×1", from the nodes that answered; null when none did. */
    static String backend(List<CacheNodeStatus> nodes) {
        String kinds = nodes.stream().filter(CacheNodeStatus::up).map(n -> n.server().split(" ")[0])
                .distinct().sorted().collect(Collectors.joining("+"));
        return kinds.isEmpty() ? null : kinds + " ×" + nodes.size();
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
