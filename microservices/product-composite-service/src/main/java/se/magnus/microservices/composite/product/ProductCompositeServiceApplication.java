package se.magnus.microservices.composite.product;

import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import tools.jackson.databind.json.JsonMapper;

@SpringBootApplication
@ComponentScan("se.magnus")
public class ProductCompositeServiceApplication {

  @Bean
  RestTemplate restTemplate(JsonMapper mapper) {
    return new RestTemplate(List.of(new JacksonJsonHttpMessageConverter(mapper)));
  }


  public static void main(String[] args) {
    SpringApplication.run(ProductCompositeServiceApplication.class, args);
  }

}
