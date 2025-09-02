package com.automate.CodeReview.Config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class MultiDataSourceConfig {

    // ---------- AutomateDB (Primary JPA) ----------
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties automateDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "automateDataSource")
    @Primary
    public DataSource automateDataSource(DataSourceProperties automateDataSourceProperties) {
        return automateDataSourceProperties
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)   // map url -> jdbcUrl ให้เอง
                .build();
    }

    // ---------- SonarQube DB (Secondary - ใช้ผ่าน JdbcTemplate) ----------
    @Bean
    @ConfigurationProperties("sonar.datasource")
    public DataSourceProperties sonarDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "sonarDataSource")
    public DataSource sonarDataSource(DataSourceProperties sonarDataSourceProperties) {
        return sonarDataSourceProperties
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "sonarJdbcTemplate")
    public JdbcTemplate sonarJdbcTemplate(@Qualifier("sonarDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }
}
