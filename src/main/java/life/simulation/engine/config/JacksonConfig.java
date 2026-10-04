package life.simulation.engine.config;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;

/**
 * A null inside a primitive array is rejected. The default coerces it, so a null
 * cell would be stored as dead.
 */
@Configuration
public class JacksonConfig {

    @Bean
    Jackson2ObjectMapperBuilderCustomizer failOnNullForPrimitives() {
        return builder -> builder.featuresToEnable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    }
}
