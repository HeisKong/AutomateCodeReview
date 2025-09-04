package com.automate.CodeReview.Config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class FlywayConfig {
    @Bean(initMethod = "migrate")
    public org.flywaydb.core.Flyway automateFlyway(
            @Qualifier("automateDataSource") DataSource ds) {
        return org.flywaydb.core.Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration/automate") // โฟลเดอร์สคริปต์
                .baselineOnMigrate(true)
                .load();
    }
}

