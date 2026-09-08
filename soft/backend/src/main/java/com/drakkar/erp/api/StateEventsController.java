package com.drakkar.erp.api;

import com.drakkar.erp.service.AuthService;
import com.drakkar.erp.service.StateEventService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class StateEventsController {
    private final AuthService auth;
    private final StateEventService events;

    public StateEventsController(AuthService auth, StateEventService events) {
        this.auth = auth;
        this.events = events;
    }

    @GetMapping(value = "/api/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestHeader("Authorization") String authorization, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        return events.subscribe(auth.tokenHash(authorization.substring(7)));
    }
}
