/*
 * Copyright 2022 the original author or authors.
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

import java.util.Optional;

import org.springframework.cloud.deployer.spi.app.AbstractActuatorTemplate;
import org.springframework.cloud.deployer.spi.app.AppAdmin;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.app.AppInstanceStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Access the actuator endpoint for an app instance deployed to Cloud Foundry.
 *
 * @author David Turanski
 */
public class CloudFoundryActuatorTemplate extends AbstractActuatorTemplate {

	public CloudFoundryActuatorTemplate(RestTemplate restTemplate, AppDeployer appDeployer, AppAdmin appAdmin) {
		super(restTemplate, appDeployer, appAdmin);
	}

	@Override
	protected String actuatorUrlForInstance(AppInstanceStatus appInstanceStatus) {
		return UriComponentsBuilder.fromHttpUrl(appInstanceStatus.getAttributes().get("url"))
				.path("/actuator").toUriString();
	}

	@Override
	public Optional<HttpHeaders> httpHeadersForInstance(AppInstanceStatus appInstanceStatus) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("X-Cf-App-Instance", String.format("%s:%d", appInstanceStatus.getAttributes()
				.get(CloudFoundryAppInstanceStatus.CF_GUID),
				Integer.valueOf(appInstanceStatus.getAttributes().get(CloudFoundryAppInstanceStatus.INDEX))));
		return Optional.of(headers);
	}
}
