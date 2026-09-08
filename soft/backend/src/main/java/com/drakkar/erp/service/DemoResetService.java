package com.drakkar.erp.service;

import com.drakkar.erp.dao.PreparationDao;
import com.drakkar.erp.domain.StateChanged;
import org.springframework.context.ApplicationEventPublisher;
import com.drakkar.erp.dao.DemoResetDao;
import com.drakkar.erp.domain.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoResetService {
    public static final Long DEFAULT_SETTLEMENT_ID = 1L;

    private final DemoResetDao dao;
    private final PreparationDao preparation;
    private final ApplicationEventPublisher events;

    public DemoResetService(DemoResetDao dao, PreparationDao preparation,
                            ApplicationEventPublisher events) {
        this.dao = dao;
        this.preparation = preparation;
        this.events = events;
    }

    @Transactional
    public void reset() {
        reset(DEFAULT_SETTLEMENT_ID);
    }

    @Transactional
    public void reset(Long settlementId) {
        if (!DEFAULT_SETTLEMENT_ID.equals(settlementId)) {
            throw DomainException.conflict(
                    "DEMO_RESET_NOT_AVAILABLE",
                    "Исходный набор данных доступен только для демонстрационного поселения");
        }
        preparation.lockSettlement(settlementId);
        dao.reset(settlementId);
        preparation.seedDemo(settlementId);
        events.publishEvent(new StateChanged(settlementId, "SETTLEMENT", settlementId));
    }
}
