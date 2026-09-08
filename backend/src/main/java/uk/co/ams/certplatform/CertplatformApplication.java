package uk.co.ams.certplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CertplatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(CertplatformApplication.class, args);
	}

}
