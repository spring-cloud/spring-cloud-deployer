/*
 * Copyright 2016-2020 the original author or authors.
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

import java.net.URL;

import javax.validation.constraints.NotNull;

import org.springframework.validation.annotation.Validated;

/**
 * Holds configuration properties for connecting to a Cloud Foundry runtime.
 *
 * @author Eric Bottard
 * @author Greg Turnquist
 */
@Validated
public class CloudFoundryConnectionProperties {

	/**
	 * Top level prefix for Cloud Foundry related configuration properties.
	 */
	public static final String CLOUDFOUNDRY_PROPERTIES = "spring.cloud.deployer.cloudfoundry";

	/**
	 * The organization to use when registering new applications.
	 */
	@NotNull
	private String org;

	/**
	 * The space to use when registering new applications.
	 */
	@NotNull
	private String space;

	/**
	 * Location of the CloudFoundry REST API endpoint to use.
	 */
	@NotNull
	private URL url;

	/**
	 * Username to use to authenticate against the Cloud Foundry API.
	 */
	@NotNull
	private String username;

	/**
	 * Password to use to authenticate against the Cloud Foundry API.
	 */
	@NotNull
	private String password;

	/**
	 * ClientId to use with token providers, effectively defaults to "cf" in
	 * cloudfroundry client.
	 */
	private String clientId;

	/**
	 * ClientSecret to use with token providers, effectively defaults to empty in
	 * cloudfroundry client.
	 */
	private String clientSecret;

	/**
	 * Indicates the identity provider to be used when accessing the Cloud Foundry API.
	 * The passed string has to be a URL-Encoded JSON Object, containing the field origin with value as origin_key of an identity provider.
	 */
	private String loginHint;

	/**
	 * Allow operation using self-signed certificates.
	 */
	private boolean skipSslValidation = false;

	public String getOrg() {
		return org;
	}

	public void setOrg(String org) {
		this.org = org;
	}

	public String getSpace() {
		return space;
	}

	public void setSpace(String space) {
		this.space = space;
	}

	public URL getUrl() {
		return url;
	}

	public void setUrl(URL url) {
		this.url = url;
	}

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	public String getClientSecret() {
		return clientSecret;
	}

	public void setClientSecret(String clientSecret) {
		this.clientSecret = clientSecret;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public boolean isSkipSslValidation() {
		return skipSslValidation;
	}

	public void setSkipSslValidation(boolean skipSslValidation) {
		this.skipSslValidation = skipSslValidation;
	}

	public String getLoginHint() {
		return loginHint;
	}

	public void setLoginHint(String loginHint) {
		this.loginHint = loginHint;
	}
}
