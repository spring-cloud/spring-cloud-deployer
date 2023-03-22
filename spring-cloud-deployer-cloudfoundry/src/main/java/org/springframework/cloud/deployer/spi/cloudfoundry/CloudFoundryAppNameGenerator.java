/*
 * Copyright 2016 the original author or authors.
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

import java.util.Random;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.StringUtils;

/**
 * CloudFoundry specific implementation of {@link AppNameGenerator}.  This takes into account the
 * configuration properties {@code enableRandomAppNamePrefix} and {@code appNamePrefix}.
 *
 * If {@code enableRandomAppNamePrefix} a random short string is prefixed to the base name.
 * It is true by default. If {@code appNamePrefix} is set, it is also prefixed before the random string.
 * By default the {@code appNamePrefix} is the value of {@code spring.application.name} if available,
 * otherwise the empty string.
 *
 * As an example, if {@code enableRandomAppNamePrefix=true}, {@code spring.application.name=server},
 * and we are deploying an application whose base name is {@code time}, then the deployed name on Cloud Foundry
 * may be {@code server-ug54dh5-time}
 *
 * @author Soby Chacko
 * @author Mark Pollack
 */
public class CloudFoundryAppNameGenerator implements AppNameGenerator, InitializingBean {

	private static final Log logger = LogFactory.getLog(CloudFoundryAppNameGenerator.class);

	/* Given that appnames are by default part of a hostname, limit to 63 chars max. */
	private static final int MAX_APPNAME_LENGTH = 63;

	private String prefixToUse = "";

	private final CloudFoundryDeploymentProperties properties;

	public CloudFoundryAppNameGenerator(CloudFoundryDeploymentProperties cloudFoundryDeploymentProperties) {
		this.properties = cloudFoundryDeploymentProperties;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		if (properties.isEnableRandomAppNamePrefix()) {
			prefixToUse = createUniquePrefix();
			if (!StringUtils.isEmpty(properties.getAppNamePrefix())) {
				prefixToUse = String.format("%s-%s", properties.getAppNamePrefix(), prefixToUse);
			}
		} else {
			if (!StringUtils.isEmpty(properties.getAppNamePrefix())) {
				prefixToUse = properties.getAppNamePrefix();
			}
		}
		logger.info(String.format("Prefix to be used for deploying apps: %s", prefixToUse));
	}


	@Override
	public String generateAppName(String appName) {
		if (StringUtils.isEmpty(prefixToUse)) {
			return appName.substring(0, Math.min(MAX_APPNAME_LENGTH, appName.length()));
		} else {
			String string = String.format("%s-%s", prefixToUse, appName);
			return string.substring(0, Math.min(MAX_APPNAME_LENGTH, string.length()));
		}
	}

	private String createUniquePrefix() {
		String alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
		char[] result = new char[7];
		Random random = new Random();
		for (int i = 0 ; i < result.length ; i++) {
			result[i] = alphabet.charAt(random.nextInt(alphabet.length()));
		}
		return new String(result);
	}


}
