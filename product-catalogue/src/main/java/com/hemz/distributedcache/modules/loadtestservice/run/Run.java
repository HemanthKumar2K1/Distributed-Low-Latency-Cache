package com.hemz.distributedcache.modules.loadtestservice.run;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A load-test run: its configuration, environment, and steps with per-second samples.
 * Saved as one JSON file. Lists are copy-on-write so the live run
 * can be serialised safely while samples are still being added.
 */
public class Run {

    private long id;
    private String label;
    private RunStatus status = RunStatus.PENDING;
    private Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;
    private RunConfig config;
    private Map<String, Object> environment = new LinkedHashMap<>();
    private final List<StepRecord> steps = new CopyOnWriteArrayList<>();
    private String failureReason;

    public void addStep(StepRecord step) { steps.add(step); }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public RunStatus getStatus() { return status; }
    public void setStatus(RunStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public RunConfig getConfig() { return config; }
    public void setConfig(RunConfig config) { this.config = config; }
    public Map<String, Object> getEnvironment() { return environment; }
    public void setEnvironment(Map<String, Object> environment) { this.environment = environment == null ? new LinkedHashMap<>() : environment; }
    public List<StepRecord> getSteps() { return steps; }
    public void setSteps(List<StepRecord> list) { steps.clear(); if (list != null) steps.addAll(list); }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
}
