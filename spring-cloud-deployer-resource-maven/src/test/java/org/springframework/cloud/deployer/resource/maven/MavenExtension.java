/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.deployer.resource.maven;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.ExceptionHandlingConfigurer;
import org.springframework.security.config.annotation.web.configurers.HttpBasicConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.User.UserBuilder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simple junit5 extension which bootstraps a server to simulate various
 * scenarious for artifact resolving via http.
 *
 * @author Janne Valkealahti
 */
public class MavenExtension implements AfterEachCallback, BeforeEachCallback {

	private ConfigurableApplicationContext context;

	public int getPort() {
		return Integer.parseInt(this.context.getEnvironment().getProperty("local.server.port"));
	}

	@Override
	public void beforeEach(ExtensionContext context) throws Exception {
		SpringApplication application = new SpringApplication(ServerConfig.class);
		this.context = application.run("--server.port=0");
	}

	@Override
	public void afterEach(ExtensionContext context) throws Exception {
		if (this.context != null) {
			this.context.close();
		}
		this.context = null;
	}

	@SpringBootApplication
	static class ServerConfig {
	}

	@RestController
	@RequestMapping("/public")
	static class PublicRepoController {

		@GetMapping(path = "/org/example/app/1.0.0.RELEASE/app-1.0.0.RELEASE.jar")
		public byte[] app100release() {
			return new byte[0];
		}

	}

	@RestController
	@RequestMapping("/private")
	static class PrivateRepoController {

		@GetMapping(path = "/org/example/secured/1.0.0.RELEASE/secured-1.0.0.RELEASE.jar")
		public byte[] secured100release() {
			return new byte[0];
		}

	}

	@RestController
	@RequestMapping("/preemptive")
	@EnableWebSecurity
	static class PreemptiveRepoController {

		@GetMapping(path = "/org/example/preemptive/1.0.0.RELEASE/preemptive-1.0.0.RELEASE.jar")
		public byte[] preemptive100release() {
			return new byte[0];
		}

	}

	@Configuration
	static class BasicSecurityConfig {

		@Bean
		public UserDetailsService userDetailsService() {
			UserBuilder users = User.builder();
			InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
			manager.createUser(users.username("user").password("{noop}password").roles("USER").build());
			return manager;
		}

		@Bean
		public HttpBasicConfigurer configureBasic(HttpSecurity http) throws Exception {

			// We add basic auth for /private so server returns 401 and
			// challenge happens with maven client.

			return http
				.authorizeHttpRequests()
					.requestMatchers("/public/**").permitAll()
					.requestMatchers("/private/**").hasRole("USER")
					.and()
				.httpBasic();
		}
	}

	@Configuration
	@Order(1)
	static class PreemptiveSecurityConfig {

		@Bean
		protected ExceptionHandlingConfigurer<HttpSecurity> configure(HttpSecurity http) throws Exception {

			// We add basic auth for /preemptive so server returns 403 as
			// exception handling is changed to force 403.
			// normal maven behaviour is that it needs 401 to continue with a challenge.
			// This is where preemptive auth takes place as client should send auth
			// with every request.

			return http
				.securityMatcher("/preemptive/**")
				.authorizeRequests(authorizeRequests ->
                    authorizeRequests.anyRequest().hasRole("USER")
				)
				.httpBasic()
					.and()
				.exceptionHandling()
					.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.FORBIDDEN));
		}
	}
}
