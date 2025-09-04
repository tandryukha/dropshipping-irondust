package com.irondust.search.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LogSseService {
    private static final Logger log = LoggerFactory.getLogger(LogSseService.class);
    private final Map<String, Sinks.Many<String>> runIdToSink = new ConcurrentHashMap<>();

    public void append(String runId, String line) {
        if (runId == null) return;
        Sinks.Many<String> sink = runIdToSink.computeIfAbsent(runId, k -> Sinks.many().multicast().onBackpressureBuffer());
        sink.tryEmitNext(line);
    }

    public Flux<ServerSentEvent<String>> stream(String runId) {
        Sinks.Many<String> sink = runIdToSink.computeIfAbsent(runId, k -> Sinks.many().multicast().onBackpressureBuffer());
        Flux<String> heartbeat = Flux.interval(Duration.ofSeconds(10)).map(i -> "");
        return Flux.merge(sink.asFlux(), heartbeat)
                .map(s -> ServerSentEvent.builder(s).build())
                .doOnCancel(() -> log.debug("SSE client disconnected for runId={}", runId));
    }
}


