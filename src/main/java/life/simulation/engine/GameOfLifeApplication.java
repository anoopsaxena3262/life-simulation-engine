package life.simulation.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import life.simulation.engine.config.GameProperties;

@SpringBootApplication
@EnableConfigurationProperties(GameProperties.class)
public class GameOfLifeApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameOfLifeApplication.class, args);
    }
}
