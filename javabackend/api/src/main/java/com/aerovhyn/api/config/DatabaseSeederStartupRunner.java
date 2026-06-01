package com.aerovhyn.api.config;

import com.aerovhyn.api.service.DatabaseSeederService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSeederStartupRunner implements CommandLineRunner {

    private final DatabaseSeederService seederService;

    public DatabaseSeederStartupRunner(DatabaseSeederService seederService) {
        this.seederService = seederService;
    }

    @Override
    public void run(String... args) throws Exception {
        seederService.seed();
    }
}
