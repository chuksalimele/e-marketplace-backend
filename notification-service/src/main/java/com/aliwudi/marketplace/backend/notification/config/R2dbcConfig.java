// R2dbcConfig.java
package com.aliwudi.marketplace.backend.notification.config;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;
import org.springframework.context.annotation.Bean;

@Configuration
@EnableR2dbcAuditing // Enables R2DBC auditing (e.g., @CreatedDate, @LastModifiedDate)
public class R2dbcConfig {

    /**
     * Configures a ConnectionFactoryInitializer to run SQL scripts on startup.
     * This is useful for schema creation or initial data population, especially for
     * in-memory databases like H2 during development.
     *
     * Note: For production, consider using Flyway or Liquibase for database migrations.
     * Schema creation is also handled via `application.yml`'s `initialStatements` for simplicity.
     * This bean is more for demonstrating how you could use external SQL files.
     *
     * @param connectionFactory The R2DBC ConnectionFactory.
     * @return A ConnectionFactoryInitializer bean.
     */
    @Bean
    public ConnectionFactoryInitializer initializer(ConnectionFactory connectionFactory) {
        ConnectionFactoryInitializer initializer = new ConnectionFactoryInitializer();
        initializer.setConnectionFactory(connectionFactory);
        // You can add more scripts here if needed, e.g., data.sql
        // initializer.setDatabasePopulator(new ResourceDatabasePopulator(new ClassPathResource("schema.sql")));
        return initializer;
    }
}
