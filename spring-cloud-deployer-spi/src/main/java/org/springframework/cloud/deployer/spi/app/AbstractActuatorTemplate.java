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

package org.springframework.cloud.deployer.spi.app;

import java.util.Collections;
import java.util.Optional;
import java.util.function.Consumer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Base class for ActuatorTemplate implementations.
 *
 * @author David Turanski
 */
public abstract class AbstractActuatorTemplate implements ActuatorOperations {

	protected final Log logger = LogFactory.getLog(getClass().getName());

	protected final RestTemplate restTemplate;

	protected final AppDeployer appDeployer;

	private Consumer<HttpHeaders> httpHeadersConsumer = headers -> {};

	protected AbstractActuatorTemplate(RestTemplate restTemplate, AppDeployer appDeployer) {
		this.restTemplate = restTemplate;
		this.appDeployer = appDeployer;
	}

	@Override
	public <T> T getFromActuator(String deploymentId, String guid, String endpoint, Class<T> responseType) {
		String actuatorUrl = getActuatorUrl(deploymentId, guid);

		ResponseEntity<T> responseEntity = httpGet(UriComponentsBuilder
				.fromHttpUrl(actuatorUrl).path(normalizePath(endpoint)).toUriString(), responseType);
		if (responseEntity.getStatusCode().isError()) {
			logger.error(responseEntity.getStatusCode().toString());
		}
		return responseEntity.getBody();
	}

	@Override
	public <T, R> R postToActuator(String deploymentId, String guid, String endpoint, T body,
			Class<R> responseType) {
		String actuatorUrl = getActuatorUrl(deploymentId, guid);
		ResponseEntity<R> responseEntity = httpPost(UriComponentsBuilder
				.fromHttpUrl(actuatorUrl).path(normalizePath(endpoint)).toUriString(), body, responseType);
		if (responseEntity.getStatusCode().isError()) {
			logger.error(responseEntity.getStatusCode().toString());
		}
		return responseEntity.getBody();
	}

	protected final HttpHeaders requestHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		headers.setContentType(MediaType.APPLICATION_JSON);
		this.httpHeadersConsumer.accept(headers);
		return headers;
	}

	protected final void setHttpHeadersConsumer(Consumer<HttpHeaders> httpHeadersConsumer) {
		Assert.notNull(httpHeadersConsumer, "'httpHeadersConsumer cannot be null");
		this.httpHeadersConsumer = httpHeadersConsumer;
	}

	private final String normalizePath(String path) {
		return path.startsWith("/") ? path : "/" + path;
	}

	private final <T> ResponseEntity<T> httpGet(String url, Class<T> responseType) {
		return restTemplate.exchange(url, HttpMethod.GET,
				new HttpEntity(requestHeaders()), responseType);
	}

	private final <T, R> ResponseEntity<R> httpPost(String url, T requestBody, Class<R> responseType) {
		HttpEntity<T> requestEntity = new HttpEntity(requestBody, requestHeaders());
		return restTemplate.exchange(url, HttpMethod.POST, requestEntity, responseType);
	}

	protected final String getActuatorUrl(String deploymentId, String guid) {
		AppInstanceStatus appInstanceStatus = getDeployedInstance(deploymentId, guid)
				.orElseThrow(() -> new IllegalStateException(
						String.format("App with deploymentId %s and guid %s not deployed", deploymentId, guid)));

		try {
			return actuatorUrlForInstance(appInstanceStatus);
		} catch (Exception e) {
			throw new IllegalArgumentException(String.format(
					"Unable to determine actuator url for app with guid %s", guid));
		}
	}

	private final Optional<AppInstanceStatus> getDeployedInstance(String deploymentId, String guid) {
		return appDeployer.status(deploymentId).getInstances().values().stream()
				.filter(appInstance -> appInstance.getState() == DeploymentState.deployed &&
						appInstance.getAttributes().get("guid").equals(guid))
				.findFirst();
	}

	protected abstract String actuatorUrlForInstance(AppInstanceStatus appInstanceStatus);

}
