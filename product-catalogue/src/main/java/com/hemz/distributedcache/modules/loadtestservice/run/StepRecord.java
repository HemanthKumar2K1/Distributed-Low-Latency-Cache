package com.hemz.distributedcache.modules.loadtestservice.run;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** One rate step inside a run. Samples are appended live while the step runs. */
public class StepRecord {

    private int index;
    private int targetRps;
    private String status = "PENDING";
    private final List<Sample> samples = new CopyOnWriteArrayList<>();
    private StepSummary summary;

    public StepRecord() {
    }

    public StepRecord(int index, int targetRps) {
        this.index = index;
        this.targetRps = targetRps;
    }

    public void addSample(Sample s) {
        samples.add(s);
    }

    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }
    public int getTargetRps() { return targetRps; }
    public void setTargetRps(int targetRps) { this.targetRps = targetRps; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<Sample> getSamples() { return samples; }
    public void setSamples(List<Sample> list) { samples.clear(); if (list != null) samples.addAll(list); }
    public StepSummary getSummary() { return summary; }
    public void setSummary(StepSummary summary) { this.summary = summary; }
}
