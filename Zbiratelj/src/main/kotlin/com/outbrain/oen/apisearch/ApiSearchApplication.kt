package com.outbrain.oen.apisearch

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableScheduling
import springfox.documentation.builders.PathSelectors
import springfox.documentation.builders.RequestHandlerSelectors
import springfox.documentation.spi.DocumentationType
import springfox.documentation.spring.web.plugins.Docket
import springfox.documentation.swagger2.annotations.EnableSwagger2


@SpringBootApplication
@EnableSwagger2
@EnableScheduling
class ApiSearchApplication {

	@Bean
	fun api(): Docket? {
		return Docket(DocumentationType.SWAGGER_2)
				.select()
				.apis(RequestHandlerSelectors.any())
				.paths(PathSelectors.any())
				.build()
	}
}

var INSTANCE: ApplicationContext? = null

fun main(args: Array<String>) {
	val env = System.getenv("APP_ENV") ?: "dev"
	val app = SpringApplication(ApiSearchApplication::class.java)
	app.setAdditionalProfiles(env)
	INSTANCE = app.run(*args)
}

