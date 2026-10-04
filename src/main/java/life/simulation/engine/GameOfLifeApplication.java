package life.simulation.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import life.simulation.engine.config.GameProperties;

@SpringBootApplication
@EnableConfigurationProperties(GameProperties.class)
public class GameOfLifeApplication {

    private static final Logger log = LoggerFactory.getLogger(GameOfLifeApplication.class);

    public static void main(String[] args) {
        log.info("starting life-simulation-engine");
        SpringApplication.run(GameOfLifeApplication.class, args);
    }
}
