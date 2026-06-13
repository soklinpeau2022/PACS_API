package com.ut.emrPacs.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Arrays;

@Configuration
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FlywayMigrationConfig {

    @Bean(initMethod = "migrate")
    @ConditionalOnMissingBean(Flyway.class)
    public Flyway flyway(
            DataSource dataSource,
            @Value("${spring.flyway.locations:classpath:db/migration}") String locations,
            @Value("${spring.flyway.baseline-on-migrate:false}") boolean baselineOnMigrate,
            @Value("${spring.flyway.clean-disabled:true}") boolean cleanDisabled,
            @Value("${spring.flyway.clean-on-validation-error:false}") boolean cleanOnValidationError,
            @Value("${spring.flyway.validate-on-migrate:true}") boolean validateOnMigrate,
            @Value("${spring.flyway.out-of-order:false}") boolean outOfOrder,
            @Value("${spring.flyway.fail-on-missing-locations:true}") boolean failOnMissingLocations
    ) {
        FluentConfiguration configuration = Flyway.configure()
                .dataSource(dataSource)
                .locations(parseLocations(locations))
                .baselineOnMigrate(baselineOnMigrate)
                .cleanDisabled(cleanDisabled)
                .cleanOnValidationError(cleanOnValidationError)
                .validateOnMigrate(validateOnMigrate)
                .outOfOrder(outOfOrder)
                .failOnMissingLocations(failOnMissingLocations);

        return configuration.load();
    }

    private static String[] parseLocations(String locations) {
        if (locations == null || locations.trim().isEmpty()) {
            return new String[] {"classpath:db/migration"};
        }
        return Arrays.stream(locations.split(","))
                .map(String::trim)
                .filter(location -> !location.isEmpty())
                .toArray(String[]::new);
    }
}
