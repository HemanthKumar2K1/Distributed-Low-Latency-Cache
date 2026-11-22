package com.hemz.distributedcache.modules.loadtestservice.web;

import com.hemz.distributedcache.modules.loadtestservice.config.LoadTestProperties;
import com.hemz.distributedcache.modules.loadtestservice.run.Run;
import com.hemz.distributedcache.modules.loadtestservice.run.RunListItem;
import com.hemz.distributedcache.modules.loadtestservice.run.RunRequest;
import com.hemz.distributedcache.modules.loadtestservice.run.RunService;
import com.hemz.distributedcache.modules.loadtestservice.run.RunStatus;
import com.hemz.distributedcache.modules.loadtestservice.run.SseHub;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/runs")
public class RunController {

    private final RunService service;
    private final SseHub hub;
    private final LoadTestProperties props;

    public RunController(RunService service, SseHub hub, LoadTestProperties props) {
        this.service = service;
        this.hub = hub;
        this.props = props;
    }

    /** 202 Accepted: the run has started, not finished. Follow it on /api/runs/{id}/stream. */
    @PostMapping
    public ResponseEntity<Map<String, Object>> start(@Valid @RequestBody RunRequest request) {
        Run run = service.start(request.toConfig(props.target()));
        return ResponseEntity.accepted()
                .location(URI.create("/api/runs/" + run.getId()))
                .body(Map.of("id", run.getId()));
    }

    @GetMapping
    public List<RunListItem> list() {
        return service.list();
    }

    @GetMapping("/active")
    public ResponseEntity<RunListItem> active() {
        return service.active().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}")
    public Run get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<Void> stop(@PathVariable long id) {
        service.stop(id);
        return ResponseEntity.accepted().build();
    }

    /**
     * Live updates. Events: snapshot (the full run, first), state, step-start, sample,
     * step, finished. For a finished run, the stream sends the snapshot and closes.
     */
    @GetMapping(path = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable long id) {
        Run run = service.get(id);
        boolean finished = run.getStatus() != RunStatus.RUNNING && run.getStatus() != RunStatus.PENDING;
        return hub.subscribe(id, run, finished);
    }
}
