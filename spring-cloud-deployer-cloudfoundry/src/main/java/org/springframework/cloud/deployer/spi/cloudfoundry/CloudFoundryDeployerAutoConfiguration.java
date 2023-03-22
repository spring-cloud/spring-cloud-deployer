/*
 * Copyright 2016-2021 the original author or authors.
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

package org.springframework.cloud.deployer.spi.cloudfoundry;

import java.time.Duration;

import com.github.zafarkhaja.semver.Version;
import org.cloudfoundry.client.CloudFoundryClient;
import org.cloudfoundry.client.v2.info.GetInfoRequest;
import org.cloudfoundry.operations.CloudFoundryOperations;
import org.cloudfoundry.operations.DefaultCloudFoundryOperations;
import org.cloudfoundry.reactor.ConnectionContext;
import org.cloudfoundry.reactor.DefaultConnectionContext;
import org.cloudfoundry.reactor.TokenProvider;
import org.cloudfoundry.reactor.client.ReactorCloudFoundryClient;
import org.cloudfoundry.reactor.tokenprovider.PasswordGrantTokenProvider;
import org.cloudfoundry.reactor.tokenprovider.PasswordGrantTokenProvider.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.cloud.deployer.spi.util.RuntimeVersionUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;


/**
 * Creates a {@link CloudFoundryAppDeployer}
 *
 * @author Eric Bottard
 * @author Ben Hale
 * @author David Turanski
 */
@Configuration
@EnableConfigurationProperties
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
public class CloudFoundryDeployerAutoConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(CloudFoundryDeployerAutoConfiguration.class);

	@Autowired
	private EarlyConnectionConfiguration connectionConfiguration;

	@Bean
	@ConditionalOnMissingBean
	public CloudFoundryOperations cloudFoundryOperations(CloudFoundryClient cloudFoundryClient, CloudFoundryConnectionProperties properties) {
		return DefaultCloudFoundryOperations.builder()
			.cloudFoundryClient(cloudFoundryClient)
			.organization(properties.getOrg())
			.space(properties.getSpace())
			.build();
	}

	private RuntimeEnvironmentInfo runtimeEnvironmentInfo(Class spiClass, Class implementationClass) {
		CloudFoundryClient client = connectionConfiguration.cloudFoundryClient(
			connectionConfiguration.connectionContext(connectionConfiguration.cloudFoundryConnectionProperties()),
			connectionConfiguration.tokenProvider(connectionConfiguration.cloudFoundryConnectionProperties()));
		Version version = connectionConfiguration.version(client);

		return new CloudFoundryPlatformSpecificInfo(new RuntimeEnvironmentInfo.Builder())
			.apiEndpoint(connectionConfiguration.cloudFoundryConnectionProperties().getUrl().toString())
			.org(connectionConfiguration.cloudFoundryConnectionProperties().getOrg())
			.space(connectionConfiguration.cloudFoundryConnectionProperties().getSpace())
			.builder()
				.implementationName(implementationClass.getSimpleName())
				.spiClass(spiClass)
				.implementationVersion(RuntimeVersionUtils.getVersion(CloudFoundryAppDeployer.class))
				.platformType("Cloud Foundry")
				.platformClientVersion(RuntimeVersionUtils.getVersion(client.getClass()))
				.platformApiVersion(version.toString())
				.platformHostVersion("unknown")
				.build();
	}

	@Bean
	@ConditionalOnMissingBean(AppDeployer.class)
	public AppDeployer appDeployer(CloudFoundryOperations operations,
		AppNameGenerator applicationNameGenerator) {
		return new CloudFoundryAppDeployer(
			applicationNameGenerator,
			connectionConfiguration.appDeploymentProperties(),
			operations,
			runtimeEnvironmentInfo(AppDeployer.class, CloudFoundryAppDeployer.class)
		);
	}

	@Bean
	CloudFoundryActuatorTemplate actuatorOperations(RestTemplate actuatorRestTemplate, AppDeployer appDeployer) {
		return new CloudFoundryActuatorTemplate(actuatorRestTemplate, appDeployer,
				connectionConfiguration.appDeploymentProperties().getAppAdmin());
	}

	@Bean
	RestTemplate actuatorRestTemplate() {
		return new RestTemplate();
	}

	@Bean
	@ConditionalOnMissingBean(AppNameGenerator.class)
	public AppNameGenerator appDeploymentCustomizer() {
		return new CloudFoundryAppNameGenerator(connectionConfiguration.appDeploymentProperties());
	}

	@Bean
	@ConditionalOnMissingBean(TaskLauncher.class)
	public TaskLauncher taskLauncher(CloudFoundryClient client,
		CloudFoundryOperations operations,
		Version version) {

		if (version.greaterThanOrEqualTo(UnsupportedVersionTaskLauncher.MINIMUM_SUPPORTED_VERSION)) {
			RuntimeEnvironmentInfo runtimeEnvironmentInfo = runtimeEnvironmentInfo(TaskLauncher.class, CloudFoundryTaskLauncher.class);
			return new CloudFoundryTaskLauncher(
				client,
				connectionConfiguration.taskDeploymentProperties(),
				operations,
				runtimeEnvironmentInfo);
		} else {
			RuntimeEnvironmentInfo runtimeEnvironmentInfo = runtimeEnvironmentInfo(TaskLauncher.class, UnsupportedVersionTaskLauncher.class);
			return new UnsupportedVersionTaskLauncher(version, runtimeEnvironmentInfo);
		}
	}

	@Bean
	@ConfigurationPropertiesBinding
	public DurationConverter durationConverter() {
		return new DurationConverter();
	}

	/**
	 * A subset of configuration beans that can be used on its own to connect to the Cloud Controller API
	 * and query it for its version. Automatically applied in CloudFoundryDeployerAutoConfiguration by virtue
	 * of being a static inner class of it.
	 *
	 * @author Eric Bottard
	 */
	@Configuration
	@EnableConfigurationProperties
	public static class EarlyConnectionConfiguration {

		@Bean
		@ConditionalOnMissingBean(name = "appDeploymentProperties")
		public CloudFoundryDeploymentProperties appDeploymentProperties() {
			return defaultSharedDeploymentProperties();
		}


		@Bean
		@ConditionalOnMissingBean(name = "taskDeploymentProperties")
		public CloudFoundryDeploymentProperties taskDeploymentProperties() {
			return defaultSharedDeploymentProperties();
		}

		@Bean
		@ConfigurationProperties(prefix = CloudFoundryConnectionProperties.CLOUDFOUNDRY_PROPERTIES)
		public CloudFoundryDeploymentProperties defaultSharedDeploymentProperties() {
			return new CloudFoundryDeploymentProperties();
		}

		@Bean
		@ConditionalOnMissingBean
		public Version version(CloudFoundryClient client) {
			return client.info()
				.get(GetInfoRequest.builder()
					.build())
				.map(response -> Version.valueOf(response.getApiVersion()))
				.doOnError(e -> {
					throw new RuntimeException("Bad credentials connecting to Cloud Foundry.", e);
				})
				.doOnNext(version -> logger.info("Connecting to Cloud Foundry with API Version {}", version))
				.block(Duration.ofSeconds(appDeploymentProperties().getApiTimeout()));
		}

		@Bean
		@ConditionalOnMissingBean
		public CloudFoundryClient cloudFoundryClient(ConnectionContext connectionContext, TokenProvider tokenProvider) {
			return ReactorCloudFoundryClient.builder()
				.connectionContext(connectionContext)
				.tokenProvider(tokenProvider)
				.build();
		}

		@Bean
		@ConditionalOnMissingBean
		public TokenProvider tokenProvider(CloudFoundryConnectionProperties properties) {
			Builder tokenProviderBuilder = PasswordGrantTokenProvider.builder()
					.username(properties.getUsername())
					.password(properties.getPassword())
					.loginHint(properties.getLoginHint());
			if (StringUtils.hasText(properties.getClientId())) {
				tokenProviderBuilder.clientId(properties.getClientId());
			}
			if (StringUtils.hasText(properties.getClientSecret())) {
				tokenProviderBuilder.clientSecret(properties.getClientSecret());
			}
			return tokenProviderBuilder.build();
		}

		@Bean
		@ConditionalOnMissingBean
		@ConfigurationProperties(prefix = CloudFoundryConnectionProperties.CLOUDFOUNDRY_PROPERTIES)
		public CloudFoundryConnectionProperties cloudFoundryConnectionProperties() {
			return new CloudFoundryConnectionProperties();
		}

		@Bean
		@ConditionalOnMissingBean
		public ConnectionContext connectionContext(CloudFoundryConnectionProperties properties) {
			return DefaultConnectionContext.builder()
				.apiHost(properties.getUrl().getHost())
				.skipSslValidation(properties.isSkipSslValidation())
				.build();
		}
	}
}
