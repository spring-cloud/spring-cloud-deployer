/*
 * Copyright 2021-2021 the original author or authors.
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

package org.springframework.cloud.deployer.spi.kubernetes;

import org.junit.jupiter.api.Test;

import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link KubernetesDeployerProperties}.
 *
 * @author Glenn Renfro
 */
public class KubernetesDeployerPropertiesTests {

	@Test
	public void testImagePullPolicyDefault() {
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		assertThat(kubernetesDeployerProperties.getImagePullPolicy()).as("Image pull policy should not be null").isNotNull();
		assertEquals(ImagePullPolicy.IfNotPresent,
				kubernetesDeployerProperties.getImagePullPolicy(),
				"Invalid default image pull policy");
	}

	@Test
	public void testImagePullPolicyCanBeCustomized() {
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		kubernetesDeployerProperties.setImagePullPolicy(ImagePullPolicy.Never);
		assertThat(kubernetesDeployerProperties.getImagePullPolicy()).as("Image pull policy should not be null").isNotNull();
		assertEquals(ImagePullPolicy.Never,
				kubernetesDeployerProperties.getImagePullPolicy(),
				"Unexpected image pull policy");
	}

	@Test
	public void testRestartPolicyDefault() {
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		assertThat(kubernetesDeployerProperties.getRestartPolicy()).as("Restart policy should not be null").isNotNull();
		assertEquals(RestartPolicy.Always,
				kubernetesDeployerProperties.getRestartPolicy(),
				"Invalid default restart policy");
	}

	@Test
	public void testRestartPolicyCanBeCustomized() {
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		kubernetesDeployerProperties.setRestartPolicy(RestartPolicy.OnFailure);
		assertThat(kubernetesDeployerProperties.getRestartPolicy()).as("Restart policy should not be null").isNotNull();
		assertEquals(RestartPolicy.OnFailure,
				kubernetesDeployerProperties.getRestartPolicy(),
				"Unexpected restart policy");
	}

	@Test
	public void testEntryPointStyleDefault() {
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		assertThat(kubernetesDeployerProperties.getEntryPointStyle()).as("Entry point style should not be null").isNotNull();
		assertEquals(EntryPointStyle.exec,
				kubernetesDeployerProperties.getEntryPointStyle(),
				"Invalid default entry point style");
	}

	@Test
	public void testEntryPointStyleCanBeCustomized() {
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		kubernetesDeployerProperties.setEntryPointStyle(EntryPointStyle.shell);
		assertThat(kubernetesDeployerProperties.getEntryPointStyle()).as("Entry point style should not be null").isNotNull();
		assertEquals(EntryPointStyle.shell,
				kubernetesDeployerProperties.getEntryPointStyle(),
				"Unexpected entry point stype");
	}

	@Test
	public void testNamespaceDefault() {
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		if (kubernetesDeployerProperties.getNamespace() == null) {
			kubernetesDeployerProperties.setNamespace("default");

			assertThat(StringUtils.hasText(kubernetesDeployerProperties.getNamespace())).as("Namespace should not be empty or null").isTrue();
			assertEquals("default", kubernetesDeployerProperties.getNamespace(), "Invalid default namespace");
		}
	}

	@Test
	public void testNamespaceCanBeCustomized() {
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		kubernetesDeployerProperties.setNamespace("myns");
		assertThat(StringUtils.hasText(kubernetesDeployerProperties.getNamespace())).as("Namespace should not be empty or null").isTrue();
		assertEquals("myns", kubernetesDeployerProperties.getNamespace(), "Unexpected namespace");
	}

	@Test
	public void testImagePullSecretDefault() {
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		assertThat(kubernetesDeployerProperties.getImagePullSecret()).as("No default image pull secret should be set").isNull();
	}

	@Test
	public void testImagePullSecretCanBeCustomized() {
		String secret = "mysecret";
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		kubernetesDeployerProperties.setImagePullSecret(secret);
		assertThat(kubernetesDeployerProperties.getImagePullSecret()).as("Image pull secret should not be null").isNotNull();
		assertEquals(secret, kubernetesDeployerProperties.getImagePullSecret(), "Unexpected image pull secret");
	}

	@Test
	public void testEnvironmentVariablesDefault() {
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		assertEquals(0,
				kubernetesDeployerProperties.getEnvironmentVariables().length,
				"No default environment variables should be set");
	}

	@Test
	public void testEnvironmentVariablesCanBeCustomized() {
		String[] envVars = new String[] { "var1=val1", "var2=val2" };
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		kubernetesDeployerProperties.setEnvironmentVariables(envVars);
		assertThat(kubernetesDeployerProperties.getEnvironmentVariables()).as("Environment variables should not be null").isNotNull();
		assertEquals(2,
				kubernetesDeployerProperties.getEnvironmentVariables().length,
				"Unexpected number of environment variables");
	}

	@Test
	public void testTaskServiceAccountNameDefault() {
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		assertThat(kubernetesDeployerProperties.getTaskServiceAccountName()).as("Task service account name should not be null").isNotNull();
		assertEquals(kubernetesDeployerProperties.DEFAULT_TASK_SERVICE_ACCOUNT_NAME,
				kubernetesDeployerProperties.getTaskServiceAccountName(),
				"Unexpected default task service account name");
	}

	@Test
	public void testTaskServiceAccountNameCanBeCustomized() {
		String taskServiceAccountName = "mysa";
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		kubernetesDeployerProperties.setTaskServiceAccountName(taskServiceAccountName);
		assertThat(kubernetesDeployerProperties.getTaskServiceAccountName()).as("Task service account name should not be null").isNotNull();
		assertEquals(taskServiceAccountName,
				kubernetesDeployerProperties.getTaskServiceAccountName(),
				"Unexpected task service account name");
	}
}
