package com.propchk.be.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
public class DataSourceConfig {

    private static final Logger logger = LoggerFactory.getLogger(DataSourceConfig.class);

    @Value("${spring.datasource.url:jdbc:postgresql://localhost:5432/propchk_dev}")
    private String rawUrl;

    @Value("${spring.datasource.username:postgres}")
    private String defaultUsername;

    @Value("${spring.datasource.password:postgres}")
    private String defaultPassword;

    @Bean
    @Primary
    public DataSource dataSource() {
        String dbUrl = clean(System.getenv("SPRING_DATASOURCE_URL"));
        if (dbUrl == null || dbUrl.isBlank()) {
            if (rawUrl != null && !rawUrl.isBlank() && !rawUrl.contains("localhost:5432")) {
                dbUrl = clean(rawUrl);
            } else if (System.getenv("DATABASE_URL") != null && !System.getenv("DATABASE_URL").isBlank()) {
                dbUrl = clean(System.getenv("DATABASE_URL"));
            } else {
                dbUrl = clean(rawUrl);
            }
        }

        String username = clean(System.getenv("SPRING_DATASOURCE_USERNAME"));
        if (username == null || username.isBlank()) {
            username = clean(defaultUsername);
        }

        String password = clean(System.getenv("SPRING_DATASOURCE_PASSWORD"));
        if (password == null || password.isBlank()) {
            password = clean(defaultPassword);
        }

        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.postgresql.Driver");

        // Normalize postgres:// or postgresql:// (without jdbc:) to standard JDBC URL and parse embedded auth
        if (dbUrl != null && (dbUrl.startsWith("postgres://") || (dbUrl.startsWith("postgresql://") && !dbUrl.startsWith("jdbc:")))) {
            try {
                URI uri = new URI(dbUrl);
                String userInfo = uri.getUserInfo();
                if (userInfo != null && userInfo.contains(":")) {
                    String[] parts = userInfo.split(":", 2);
                    username = parts[0];
                    password = parts[1];
                }

                String host = uri.getHost();
                int port = uri.getPort() == -1 ? 5432 : uri.getPort();
                String path = uri.getPath();
                String query = uri.getQuery();

                dbUrl = "jdbc:postgresql://" + host + ":" + port + path + (query != null ? "?" + query : "");
                logger.info("Normalized PostgreSQL DataSource URL from URI for host: {}, port: {}", host, port);
            } catch (Exception e) {
                logger.error("Failed to parse PostgreSQL URI, falling back to raw URL: {}", dbUrl, e);
            }
        }

        config.setJdbcUrl(dbUrl);
        config.setUsername(username);
        config.setPassword(password);
        logger.info("Initializing DataSource with URL: {}", dbUrl);

        return new HikariDataSource(config);
    }

    private String clean(String val) {
        if (val == null) return null;
        val = val.trim();
        if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
            val = val.substring(1, val.length() - 1).trim();
        }
        return val;
    }
}
