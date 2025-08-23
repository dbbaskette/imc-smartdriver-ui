package com.insurancemegacorp.monitoring.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "greenplum.use-real-data", havingValue = "true")
// Also responds to GREENPLUM_USE_REAL_DATA environment variable via application.yml mapping
public class DatabaseConfig {

    @Value("${greenplum.host:${GREENPLUM_HOST:big-data-001.kuhn-labs.com}}")
    private String host;
    
    @Value("${greenplum.port:${GREENPLUM_PORT:5432}}")
    private int port;
    
    @Value("${greenplum.database:${TARGET_DATABASE:${GREENPLUM_DATABASE:insurance_megacorp}}}")
    private String database;
    
    @Value("${greenplum.user:${GREENPLUM_USER:gpadmin}}")
    private String user;
    
    @Value("${greenplum.password:${GREENPLUM_PASSWORD:}}")
    private String password;

    @Bean
    public DataSource dataSource() {
        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
        
        log.info("=== CREATING GREENPLUM DATASOURCE ====");
        log.info("DatabaseConfig @ConditionalOnProperty triggered - real data mode enabled");
        log.info("JDBC URL: {}", jdbcUrl);
        log.info("Database User: {}", user);
        log.info("Host: {}, Port: {}, Database: {}", host, port, database);
        
        return DataSourceBuilder.create()
                .driverClassName("org.postgresql.Driver")
                .url(jdbcUrl)
                .username(user)
                .password(password)
                .build();
    }
}