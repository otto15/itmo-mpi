package com.drakkar.erp.service;

import com.drakkar.erp.dao.PreparationDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationScheduler {
    private static final Logger log = LoggerFactory.getLogger(ReservationScheduler.class);
    private final PreparationDao dao;
    private final ReservationService reservations;

    public ReservationScheduler(PreparationDao dao, ReservationService reservations) {
        this.dao = dao;
        this.reservations = reservations;
    }

    @Scheduled(fixedDelayString = "${drakkar.reservations.sweep-ms}")
    public void releaseExpired() {
        for (var candidate : dao.expired()) {
            try {
                reservations.expire(candidate);
            } catch (RuntimeException ex) {
                log.warn("Could not release reservation {}", candidate.id(), ex);
            }
        }
    }
}
