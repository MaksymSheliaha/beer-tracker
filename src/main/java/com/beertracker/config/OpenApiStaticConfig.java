package com.beertracker.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Configures Spring MVC resource handlers and route redirects to serve the API-First
 * OpenAPI specification ({@code /openapi.yaml}) and embedded Swagger UI ({@code /swagger-ui.html}).
 */
@Configuration
public class OpenApiStaticConfig implements WebMvcConfigurer {

    /**
     * Exposes {@code /openapi.yaml} from the workspace {@code api/} directory with fallback
     * to packaged {@code classpath:/static/openapi.yaml}.
     *
     * @param registry Spring MVC resource handler registry
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String openApiDir = Paths.get("api").toAbsolutePath().toUri().toString();
        if (!openApiDir.endsWith("/")) {
            openApiDir += "/";
        }
        registry.addResourceHandler("/openapi.yaml")
                .addResourceLocations(openApiDir, "classpath:/static/");
    }

    /**
     * Redirects convenience entry routes ({@code /}, {@code /swagger}, {@code /docs})
     * to the embedded Swagger UI page.
     *
     * @param registry Spring MVC view controller registry
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/swagger-ui.html");
        registry.addRedirectViewController("/swagger", "/swagger-ui.html");
        registry.addRedirectViewController("/docs", "/swagger-ui.html");
    }
}
