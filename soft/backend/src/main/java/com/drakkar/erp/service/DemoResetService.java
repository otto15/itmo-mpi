package com.drakkar.erp.service;

import com.drakkar.erp.dao.DemoResetDao;
import com.drakkar.erp.domain.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoResetService {
    public static final Long DEFAULT_SETTLEMENT_ID = 1L;

    private final DemoResetDao dao;

    public DemoResetService(DemoResetDao dao) {
        this.dao = dao;
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
        dao.reset(settlementId);
    }
}
