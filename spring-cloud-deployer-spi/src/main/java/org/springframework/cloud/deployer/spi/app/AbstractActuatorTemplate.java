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

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Optional;

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

	private final AppAdmin appAdmin;

	private final Optional<String> defaultAuthenticationHeaderValue;

	protected AbstractActuatorTemplate(RestTemplate restTemplate, AppDeployer appDeployer, AppAdmin appAdmin) {
		Assert.notNull(restTemplate, "'restTemplate' is required.");
		Assert.notNull(appDeployer, "'appDeployer' is required.");
		Assert.notNull(appAdmin, "'appAdmin' is required.");
		this.restTemplate = restTemplate;
		this.appDeployer = appDeployer;
		this.appAdmin = appAdmin;
		this.defaultAuthenticationHeaderValue = prepareDefaultAthentication(appAdmin);
	}


	@Override
	public <T> T getFromActuator(String deploymentId, String guid, String endpoint, Class<T> responseType,
			Optional<HttpHeaders> optionalRequestHeaders) {

		AppInstanceStatus appInstanceStatus = getDeployedInstance(deploymentId, guid)
				.orElseThrow(() -> new IllegalStateException(
						String.format("App with deploymentId %s and guid %s not deployed", deploymentId, guid)));

		String actuatorUrl = getActuatorUrl(appInstanceStatus);

		HttpHeaders requestHeaders = requestHeaders(httpHeadersForInstance(appInstanceStatus), optionalRequestHeaders);

		ResponseEntity<T> responseEntity = httpGet(UriComponentsBuilder
				.fromHttpUrl(actuatorUrl).path(normalizePath(endpoint)).toUriString(), responseType, requestHeaders);
		if (responseEntity.getStatusCode().isError()) {
			logger.error(responseEntity.getStatusCode().toString());
		}
		return responseEntity.getBody();
	}

	@Override
	public <T, R> R postToActuator(String deploymentId, String guid, String endpoint, T body,
			Class<R> responseType, Optional<HttpHeaders> optionalRequestHeaders) {
		AppInstanceStatus appInstanceStatus = getDeployedInstance(deploymentId, guid)
				.orElseThrow(() -> new IllegalStateException(
						String.format("App with deploymentId %s and guid %s not deployed", deploymentId, guid)));

		String actuatorUrl = getActuatorUrl(appInstanceStatus);

		HttpHeaders requestHeaders = requestHeaders(httpHeadersForInstance(appInstanceStatus), optionalRequestHeaders);

		ResponseEntity<R> responseEntity = httpPost(UriComponentsBuilder
				.fromHttpUrl(actuatorUrl).path(normalizePath(endpoint)).toUriString(), body, responseType,
				requestHeaders);
		if (responseEntity.getStatusCode().isError()) {
			logger.error(responseEntity.getStatusCode().toString());
		}
		return responseEntity.getBody();
	}

	protected final String getActuatorUrl(AppInstanceStatus appInstanceStatus) {
		try {
			return actuatorUrlForInstance(appInstanceStatus);
		} catch (Exception e) {
			throw new IllegalArgumentException(String.format(
					"Unable to determine actuator url for app with guid %s",
					appInstanceStatus.getAttributes().get("guid")));
		}
	}

	protected abstract String actuatorUrlForInstance(AppInstanceStatus appInstanceStatus);

	/**
	 * Hook to allow subclasses to add special headers derived {@link AppInstanceStatus} metadata if necessary.
	 *
	 * @param appInstanceStatus the AppInstanceStatus for the target instance.
	 * @return HttpHeaders
	 */
	protected Optional<HttpHeaders> httpHeadersForInstance(AppInstanceStatus appInstanceStatus) {
		return Optional.empty();
	}


	private final HttpHeaders requestHeaders(Optional<HttpHeaders> optionalAppInstanceHeaders,
			Optional<HttpHeaders> optionalRequestHeaders) {
		HttpHeaders requestHeaders = optionalAppInstanceHeaders
				.orElse(new HttpHeaders());
		optionalRequestHeaders.ifPresent(requestHeaders::addAll);

		//Any pass-thru auth overrides the default.
		if (!requestHeaders.containsKey(HttpHeaders.AUTHORIZATION)) {
			this.defaultAuthenticationHeaderValue.ifPresent(auth -> requestHeaders.setBasicAuth(auth));
		}

		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		return requestHeaders;
	}

	private final String normalizePath(String path) {
		return path.startsWith("/") ? path : "/" + path;
	}

	private final <T> ResponseEntity<T> httpGet(String url, Class<T> responseType, HttpHeaders
			requestHeaders) {
		return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity(requestHeaders), responseType);
	}

	private final <T, R> ResponseEntity<R> httpPost(String url, T requestBody, Class<R> responseType,
			HttpHeaders requestHeaders) {
		return restTemplate.exchange(url, HttpMethod.POST,
				new HttpEntity(requestBody, requestHeaders), responseType);
	}

	private final Optional<AppInstanceStatus> getDeployedInstance(String deploymentId, String guid) {
		AppStatus appStatus = appDeployer.status(deploymentId);
		long count = appStatus.getInstances().values().stream().filter(
				appInstance -> appInstance.getAttributes().get("guid").equals(guid)).count();

		if (count == 0) {
			return Optional.empty();
		}
		else if (count > 1) {
			throw new IllegalStateException(String.format(
					"guid %s is not unique for instances of deploymentId %s", guid, deploymentId));
		}

		return appStatus.getInstances().values().stream()
				.filter(appInstance -> appInstance.getState() == DeploymentState.deployed &&
						appInstance.getAttributes().get("guid").equals(guid))
				.findFirst();
	}

	private Optional<String> prepareDefaultAthentication(AppAdmin appAdmin) {
		Optional<String> encodeBasicAuth;
		encodeBasicAuth = appAdmin.hasCredentials() ?
			Optional.of(HttpHeaders.encodeBasicAuth(appAdmin.getUser(), appAdmin.getPassword(),
					Charset.defaultCharset())) : Optional.empty();

		if (!encodeBasicAuth.isPresent()) {
			logger.warn("No app admin credentials have been configured for " + this.getClass().getName());
		}
		return encodeBasicAuth;
	}

}
