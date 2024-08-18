package de.leidenheit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class Application implements CommandLineRunner {

    private final ArazzoService arazzoService;

    public Application(ArazzoService arazzoService) {
        this.arazzoService = arazzoService;
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        arazzoService.arazzoMe(
                "documentation/workflow.yaml",
                "documentation/openapi.yaml");
    }
}