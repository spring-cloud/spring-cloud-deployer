/*
 * Copyright 2018 the original author or authors.
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
package org.springframework.cloud.deployer.autoconfigure;

import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.deployer.resource.maven.MavenProperties;
import org.springframework.cloud.deployer.resource.support.DelegatingResourceLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ResourceLoadingAutoConfiguration}.
 *
 * @author Janne Valkealahti
 *
 */
public class ResourceLoadingAutoConfigurationTests {

	private final static ResourceLoader mockResourceLoader = new ResourceLoader() {

		@Override
		public Resource getResource(String location) {
			return null;
		}

		@Override
		public ClassLoader getClassLoader() {
			return null;
		}
	};

	private final static Condition<DelegatingResourceLoader> mavenCondition = new Condition<>(
			l -> l.getLoaders().containsKey("maven"), "maven loader");

	private final static Condition<DelegatingResourceLoader> mavenReplacedCondition = new Condition<>(
			l -> l.getLoaders().get("maven").equals(mockResourceLoader), "maven loader replaced");

	private final static Condition<DelegatingResourceLoader> foobarCondition = new Condition<>(
			l -> l.getLoaders().containsKey("foobar"), "foobar mock loader");

	private final static Condition<MavenProperties> offlineCondition = new Condition<>(
			p -> p.isOffline(), "offline");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(ResourceLoadingAutoConfiguration.class));


	@Test
	public void testAutoConfigNoProperties() {
		this.contextRunner
				.run((context) -> {
					assertThat(context).hasSingleBean(DelegatingResourceLoader.class);
					assertThat(context).getBean(DelegatingResourceLoader.class).has(mavenCondition);
				});
	}

	@Test
	public void testMavenProperties() {
		this.contextRunner
				.withPropertyValues("maven.offline=true")
				.run((context) ->
					assertThat(context).getBean(MavenProperties.class).has(offlineCondition));
	}

	@Test
	public void testBuilderRegistration() {
		this.contextRunner
				.withUserConfiguration(CustomBuilderCustomizerConfig.class)
				.run((context) ->
					assertThat(context).getBean(DelegatingResourceLoader.class).has(foobarCondition));
	}

	@Test
	public void testBuilderOrderRegistration() {
		this.contextRunner
				.withUserConfiguration(MavenReplacingBuilderCustomizerConfig.class)
				.run((context) ->
					assertThat(context).getBean(DelegatingResourceLoader.class).has(mavenReplacedCondition));
	}

	@Configuration
	static class CustomBuilderCustomizerConfig {

		@Bean
		public DelegatingResourceLoaderBuilderCustomizer foobarDelegatingResourceLoaderBuilderCustomizer() {
			return customizer -> customizer.loader("foobar", mockResourceLoader);
		}
	}

	@Configuration
	static class MavenReplacingBuilderCustomizerConfig {

		@Bean
		@Order(Ordered.LOWEST_PRECEDENCE)
		public DelegatingResourceLoaderBuilderCustomizer foobarDelegatingResourceLoaderBuilderCustomizer() {
			return customizer -> customizer.loader("maven", mockResourceLoader);
		}
	}
}
