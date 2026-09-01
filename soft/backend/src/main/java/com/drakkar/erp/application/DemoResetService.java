package com.drakkar.erp.application;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

@Service
public class DemoResetService {
    private final DataSource dataSource;

    public DemoResetService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void reset() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/demo-reset.sql"));
        populator.setSeparator(";");
        populator.execute(dataSource);
    }
}
