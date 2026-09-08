package com.drakkar.erp.service;

import com.drakkar.erp.dao.AuthDao;
import com.drakkar.erp.dao.EventAccessDao;
import com.drakkar.erp.domain.StateChanged;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class StateEventService {
    private final AuthDao auth;
    private final EventAccessDao access;
    private final Duration heartbeat;
    private final long timeout;
    private final Map<SseEmitter, Connection> connections = new ConcurrentHashMap<>();
    private final ArrayBlockingQueue<StateChanged> pending = new ArrayBlockingQueue<>(1024);
    private final AtomicBoolean resync = new AtomicBoolean();

    public StateEventService(AuthDao auth, EventAccessDao access,
                             @Value("${drakkar.events.heartbeat}") Duration heartbeat,
                             @Value("${drakkar.events.connection-timeout-ms}") long timeout) {
        this.auth = auth;
        this.access = access;
        this.heartbeat = heartbeat;
        this.timeout = timeout;
    }

    public SseEmitter subscribe(String sessionKey) {
        SseEmitter emitter = new SseEmitter(timeout);
        connections.put(emitter, new Connection(sessionKey));
        emitter.onCompletion(() -> connections.remove(emitter));
        emitter.onTimeout(() -> { connections.remove(emitter); emitter.complete(); });
        emitter.onError(error -> connections.remove(emitter));
        return emitter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void committed(StateChanged event) {
        // Delivery is outside the business transaction; a disconnected browser cannot fail a command.
        if (!pending.offer(event)) resync.set(true);
    }

    @Scheduled(fixedDelayString = "${drakkar.events.dispatch-ms}")
    public void dispatch() {
        var batch = new ArrayList<StateChanged>();
        pending.drainTo(batch);
        boolean refreshAll = resync.getAndSet(false);
        Instant now = Instant.now();
        connections.forEach((emitter, connection) -> {
            try {
                var user = auth.findActiveSessionUser(connection.sessionKey);
                if (user == null) {
                    connections.remove(emitter);
                    emitter.complete();
                    return;
                }
                boolean changed = refreshAll || batch.stream().anyMatch(event -> access.canReceive(user, event));
                if (connection.lastSent == null || changed || connection.lastSent.plus(heartbeat).isBefore(now)) {
                    String event = connection.lastSent == null ? "connected" : changed ? "refresh" : "heartbeat";
                    emitter.send(SseEmitter.event().name(event).data("{}"));
                    connection.lastSent = now;
                }
            } catch (Exception ex) {
                connections.remove(emitter);
                emitter.completeWithError(ex);
            }
        });
    }

    private static class Connection {
        final String sessionKey;
        Instant lastSent;
        Connection(String sessionKey) { this.sessionKey = sessionKey; }
    }
}
