package de.wthack.mcp.steam;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Main implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

    @Bean
    public ToolCallbackProvider tools(SteamService steamService) {
        return  MethodToolCallbackProvider.builder().toolObjects(steamService).build();
    }

    @Override
    public void run(String... args) throws Exception {

    }
}
