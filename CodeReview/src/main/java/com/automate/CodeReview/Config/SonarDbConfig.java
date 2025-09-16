package com.automate.CodeReview.Config;

import javax.sql.DataSource;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class SonarDbConfig {

    @Bean(name = "sonarDataSource")
    public DataSource sonarDataSource(
            @org.springframework.beans.factory.annotation.Value("${sonar.datasource.url}") String url,
            @org.springframework.beans.factory.annotation.Value("${sonar.datasource.username}") String user,
            @org.springframework.beans.factory.annotation.Value("${sonar.datasource.password}") String pass,
            @org.springframework.beans.factory.annotation.Value("${sonar.datasource.driver-class-name}") String driver) {
        return DataSourceBuilder.create().url(url).username(user).password(pass).driverClassName(driver).build();
    }

    @Bean(name = "sonarJdbc")
    public JdbcTemplate sonarJdbc(@org.springframework.beans.factory.annotation.Qualifier("sonarDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }
}
