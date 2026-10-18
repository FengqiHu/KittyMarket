package com.example.maoliang;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@SpringBootApplication
public class MaoliangApplication {

	public static void main(String[] args) {
		SpringApplication.run(MaoliangApplication.class, args);
	}

	@Configuration
	public class WebConfig implements WebMvcConfigurer {
		@Value("${app.catalog.images-dir:../data/images}")
		private String catalogImagesDir;

		@Override
		public void addResourceHandlers(ResourceHandlerRegistry registry) {
			String imageLocation = Path.of(catalogImagesDir).toAbsolutePath().normalize().toUri().toString();
			if (!imageLocation.endsWith("/")) imageLocation += "/";
			registry.addResourceHandler("/catalog/images/**")
					.addResourceLocations(imageLocation)
					.setCachePeriod(3600);
			registry.addResourceHandler("/img/**")
					.addResourceLocations(Path.of("img").toAbsolutePath().normalize().toUri().toString() + "/")
					.setCachePeriod(3600);
			registry.addResourceHandler("/**")
					.addResourceLocations("classpath:/static/")
					.setCachePeriod(0);
		}
	}

	@RestController
	public class ApiController {
		@GetMapping("/api/data")
		@CrossOrigin(origins = "http://localhost:3000/") // Adjust origin as needed
		public String getData() {
			// Your API logic here
			return "Data from Spring Boot API";
		}
	}

}
