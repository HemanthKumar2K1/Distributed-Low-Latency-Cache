package com.hemz.distributedcache.modules.loadtestservice.run;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Pushes live updates to browsers with Server-Sent Events.
 * SSE instead of polling: one long-lived connection, one direction, and the
 * browser's EventSource reconnects on its own.
 */
@Component
public class SseHub {

    private final Map<Long, List<SseEmitter>> byRun = new ConcurrentHashMap<>();

    /**
     * Registers first, then sends the full snapshot, so no event can fall between the two.
     * The browser may see a sample twice (in the snapshot and as an event); it de-duplicates.
     */
    public SseEmitter subscribe(long runId, Object snapshot, boolean alreadyFinished) {
        SseEmitter emitter = new SseEmitter(0L);                  // no timeout: runs can be long
        List<SseEmitter> list = byRun.computeIfAbsent(runId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable remove = () -> list.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(t -> remove.run());
        try {
            emitter.send(SseEmitter.event().name("snapshot").data(snapshot, MediaType.APPLICATION_JSON));
            if (alreadyFinished) {
                emitter.complete();
            }
        } catch (IOException e) {
            remove.run();
            emitter.completeWithError(e);
        }
        return emitter;
    }

    public void publish(long runId, String event, Object data) {
        List<SseEmitter> list = byRun.get(runId);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                list.remove(emitter);                              // tab closed; never let it break the run
            }
        }
    }

    public void complete(long runId) {
        List<SseEmitter> list = byRun.remove(runId);
        if (list != null) {
            list.forEach(SseEmitter::complete);
        }
    }
}
