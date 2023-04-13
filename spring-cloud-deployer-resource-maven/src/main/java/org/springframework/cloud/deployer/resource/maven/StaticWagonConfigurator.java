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

import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.providers.http.HttpWagon;
import org.apache.maven.wagon.shared.http.HttpConfiguration;
import org.apache.maven.wagon.shared.http.HttpMethodConfiguration;
import org.eclipse.aether.transport.wagon.WagonConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.deployer.resource.maven.MavenProperties.WagonHttpMethod;
import org.springframework.cloud.deployer.resource.maven.MavenProperties.WagonHttpMethodProperties;

/**
 * Simple implementation of a {@link WagonConfigurator} which creates and supports
 * those providers we need. Maven resolver itself only provides PlexusWagonConfigurator
 * which is more involved with actual maven pom configuration and would not
 * suit our needs as things get a bit crazy with it due to its use of Guice.
 *
 * @author Janne Valkealahti
 * @autor Corneil du Plessis
 *
 */
public class StaticWagonConfigurator implements WagonConfigurator {

	private static final Logger logger = LoggerFactory.getLogger(StaticWagonConfigurator.class);

	@Override
	public void configure(Wagon wagon, Object configuration) throws Exception {
		logger.debug("Configuring wagon {} with {}", wagon, configuration);
		if (wagon instanceof HttpWagon && configuration instanceof MavenProperties.Wagon) {
			HttpWagon httpWagon = (HttpWagon)wagon;
			Map<WagonHttpMethod, WagonHttpMethodProperties> httpMethodProperties = ((MavenProperties.Wagon) configuration)
					.getHttp();
			HttpConfiguration httpConfiguration = new HttpConfiguration();
			for (Entry<WagonHttpMethod, WagonHttpMethodProperties> entry : httpMethodProperties.entrySet()) {
				switch (entry.getKey()) {
					case all:
						httpConfiguration.setAll(buildConfig(entry.getValue()));
						break;
					case get:
						httpConfiguration.setGet(buildConfig(entry.getValue()));
						break;
					case head:
						httpConfiguration.setHead(buildConfig(entry.getValue()));
						break;
					case put:
						httpConfiguration.setPut(buildConfig(entry.getValue()));
						break;
					default:
						break;
				}
			}
			httpWagon.setHttpConfiguration(httpConfiguration);
		}
	}

	private static HttpMethodConfiguration buildConfig(WagonHttpMethodProperties properties) {
		HttpMethodConfiguration config = new HttpMethodConfiguration();
		config.setUsePreemptive(properties.isUsePreemptive());
		config.setUseDefaultHeaders(properties.isUseDefaultHeaders());
		if (properties.getConnectionTimeout() != null) {
			config.setConnectionTimeout(properties.getConnectionTimeout());
		}
		if (properties.getReadTimeout() != null) {
			config.setReadTimeout(properties.getReadTimeout());
		}
		Properties params = new Properties();
		params.putAll(properties.getParams());
		config.setParams(params);
		Properties headers = new Properties();
		headers.putAll(properties.getHeaders());
		config.setHeaders(headers);
		return config;
	}
}
