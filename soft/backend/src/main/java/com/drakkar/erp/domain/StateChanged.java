package com.drakkar.erp.domain;

public record StateChanged(Long settlementId, String aggregateType, Long aggregateId) {}
