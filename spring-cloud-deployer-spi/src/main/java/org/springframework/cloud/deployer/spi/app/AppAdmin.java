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

import java.util.Map;

import org.springframework.util.StringUtils;

public class AppAdmin {
	public static final String ADMIN_USER_KEY = "SPRING_CLOUD_STREAMAPP_SECURITY_ADMIN-USER";
	public static final String ADMIN_PASSWORD_KEY = "SPRING_CLOUD_STREAMAPP_SECURITY_ADMIN-PASSWORD";
	public static final String ADMIN_USER_PROPERTY_KEY = "spring.cloud.streamapp.security.admin-user";
	public static final String ADMIN_PASSWORD_PROPERTY_KEY = "spring.cloud.streamapp.security.admin-password";
	/**
	 * Username used to access protected application resources, e.g., POST to actuator.
	 */
	private String user;

	/**
	 * Password used to access protected application resources, e.g., POST to actuator.
	 */
	private  String password;

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public boolean hasCredentials() {
		return StringUtils.hasText(this.user) &&  StringUtils.hasText(this.password);
	}

	public void addCredentialsToAppEnvironment(Map<String, String> environment) {
		/*
		 * These are configured credentials which the app security can use configure an authorized user
		 * to access protected resources, for example, ActuatorOperations.postToActuator() provides these credentials.
		 * This is intended to work for any app autoconfigured with spring-cloud-stream-applications-common-security,
		 * as are the pre-packaged stream apps.
		 */
		if (this.hasCredentials()) {
			environment.put(ADMIN_USER_KEY, this.user);
			environment.put(ADMIN_PASSWORD_KEY, this.password);
		}
	}

	public void addCredentialsToAppEnvironmentAsProperties(Map<String, String> environment) {
		/*
		 * These are configured credentials which the app security can use configure an authorized user
		 * to access protected resources, for example, ActuatorOperations.postToActuator() provides these credentials.
		 * This is intended to work for any app autoconfigured with spring-cloud-stream-applications-common-security,
		 * as are the pre-packaged stream apps.
		 */
		if (this.hasCredentials()) {
			environment.put(ADMIN_USER_PROPERTY_KEY, this.user);
			environment.put(ADMIN_PASSWORD_PROPERTY_KEY, this.password);
		}
	}
}
