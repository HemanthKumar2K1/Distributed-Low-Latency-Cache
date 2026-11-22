package com.hemz.distributedcache.modules.loadtestservice.web;

import com.hemz.distributedcache.modules.loadtestservice.config.LoadTestProperties;
import com.hemz.distributedcache.modules.loadtestservice.engine.KeyDistribution;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Defaults and limits, so the dashboard never hard-codes them. */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    /** A sweep that doubles the rate each step. */
    static final List<Integer> DEFAULT_RATES = List.of(100, 200, 400, 800, 1600, 3200);
    static final int DEFAULT_DURATION_SECONDS = 30;
    static final int DEFAULT_WARMUP_SECONDS = 10;
    static final int DEFAULT_CONNECTIONS = 200;
    static final KeyDistribution DEFAULT_DISTRIBUTION = KeyDistribution.ZIPFIAN;
    static final double DEFAULT_ZIPF_EXPONENT = 0.99;

    private final LoadTestProperties props;

    public ConfigController(LoadTestProperties props) {
        this.props = props;
    }

    @GetMapping
    public Map<String, Object> config() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("rates", DEFAULT_RATES);
        defaults.put("durationSeconds", DEFAULT_DURATION_SECONDS);
        defaults.put("warmupSeconds", DEFAULT_WARMUP_SECONDS);
        defaults.put("connections", DEFAULT_CONNECTIONS);
        defaults.put("distribution", DEFAULT_DISTRIBUTION.name());
        defaults.put("zipfExponent", DEFAULT_ZIPF_EXPONENT);
        defaults.put("maxId", props.defaultMaxId());

        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("maxRate", props.maxRate());
        limits.put("maxDurationSeconds", props.maxDurationSeconds());
        limits.put("maxConnections", props.maxConnections());
        limits.put("maxSteps", props.maxSteps());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("defaults", defaults);
        out.put("limits", limits);
        return out;
    }
}
