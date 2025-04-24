/*
 * Copyright 2018-2021 the original author or authors.
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
 * Tests for {@link KubernetesSchedulerProperties}.
 *
 * @author Chris Schaefer
 */
public class KubernetesSchedulerPropertiesTests {

	@Test
	public void testImagePullPolicyDefault() {
		KubernetesSchedulerProperties kubernetesSchedulerProperties = new KubernetesSchedulerProperties();
		assertThat(kubernetesSchedulerProperties.getImagePullPolicy()).as("Image pull policy should not be null").isNotNull();
		assertEquals(ImagePullPolicy.IfNotPresent,
				kubernetesSchedulerProperties.getImagePullPolicy(),
				"Invalid default image pull policy");
	}

	@Test
	public void testImagePullPolicyCanBeCustomized() {
		KubernetesSchedulerProperties kubernetesSchedulerProperties = new KubernetesSchedulerProperties();
		kubernetesSchedulerProperties.setImagePullPolicy(ImagePullPolicy.Never);
		assertThat(kubernetesSchedulerProperties.getImagePullPolicy()).as("Image pull policy should not be null").isNotNull();
		assertEquals(ImagePullPolicy.Never,
				kubernetesSchedulerProperties.getImagePullPolicy(),
				"Unexpected image pull policy");
	}

	@Test
	public void testRestartPolicyDefault() {
		KubernetesSchedulerProperties kubernetesSchedulerProperties = new KubernetesSchedulerProperties();
		assertThat(kubernetesSchedulerProperties.getRestartPolicy()).as("Restart policy should not be null").isNotNull();
		assertEquals(RestartPolicy.Never,
				kubernetesSchedulerProperties.getRestartPolicy(),
				"Invalid default restart policy");
	}

	@Test
	public void testRestartPolicyCanBeCustomized() {
		KubernetesSchedulerProperties kubernetesSchedulerProperties = new KubernetesSchedulerProperties();
		kubernetesSchedulerProperties.setRestartPolicy(RestartPolicy.OnFailure);
		assertThat(kubernetesSchedulerProperties.getRestartPolicy()).as("Restart policy should not be null").isNotNull();
		assertEquals(RestartPolicy.OnFailure,
				kubernetesSchedulerProperties.getRestartPolicy(),
				"Unexpected restart policy");
	}

	@Test
	public void testEntryPointStyleDefault() {
		KubernetesSchedulerProperties kubernetesSchedulerProperties = new KubernetesSchedulerProperties();
		assertThat(kubernetesSchedulerProperties.getEntryPointStyle()).as("Entry point style should not be null").isNotNull();
		assertEquals(EntryPointStyle.exec,
				kubernetesSchedulerProperties.getEntryPointStyle(),
				"Invalid default entry point style");
	}

	@Test
	public void testEntryPointStyleCanBeCustomized() {
		KubernetesSchedulerProperties kubernetesSchedulerProperties = new KubernetesSchedulerProperties();
		kubernetesSchedulerProperties.setEntryPointStyle(EntryPointStyle.shell);
		assertThat(kubernetesSchedulerProperties.getEntryPointStyle()).as("Entry point style should not be null").isNotNull();
		assertEquals(EntryPointStyle.shell,
				kubernetesSchedulerProperties.getEntryPointStyle(),
				"Unexpected entry point stype");
	}

	@Test
	public void testNamespaceDefault() {
		KubernetesSchedulerProperties kubernetesSchedulerProperties = new KubernetesSchedulerProperties();
		if (kubernetesSchedulerProperties.getNamespace() == null) {
			kubernetesSchedulerProperties.setNamespace("default");

			assertThat(StringUtils.hasText(kubernetesSchedulerProperties.getNamespace())).as("Namespace should not be empty or null").isTrue();
			assertEquals("default", kubernetesSchedulerProperties.getNamespace(), "Invalid default namespace");
		}
	}

	@Test
	public void testNamespaceCanBeCustomized() {
		KubernetesSchedulerProperties kubernetesSchedulerProperties = new KubernetesSchedulerProperties();
		kubernetesSchedulerProperties.setNamespace("myns");
		assertThat(StringUtils.hasText(kubernetesSchedulerProperties.getNamespace())).as("Namespace should not be empty or null").isTrue();
		assertEquals("myns", kubernetesSchedulerProperties.getNamespace(), "Unexpected namespace");
	}

	@Test
	public void testImagePullSecretDefault() {
		KubernetesSchedulerProperties kubernetesSchedulerProperties = new KubernetesSchedulerProperties();
		assertThat(kubernetesSchedulerProperties.getImagePullSecret()).as("No default image pull secret should be set").isNull();
	}

	@Test
	public void testImagePullSecretCanBeCustomized() {
		String secret = "mysecret";
		KubernetesSchedulerProperties kubernetesSchedulerProperties = new KubernetesSchedulerProperties();
		kubernetesSchedulerProperties.setImagePullSecret(secret);
		assertThat(kubernetesSchedulerProperties.getImagePullSecret()).as("Image pull secret should not be null").isNotNull();
		assertEquals(secret, kubernetesSchedulerProperties.getImagePullSecret(), "Unexpected image pull secret");
	}

	@Test
	public void testEnvironmentVariablesDefault() {
		KubernetesSchedulerProperties kubernetesSchedulerProperties = new KubernetesSchedulerProperties();
		assertEquals(0,
				kubernetesSchedulerProperties.getEnvironmentVariables().length,
				"No default environment variables should be set");
	}

	@Test
	public void testEnvironmentVariablesCanBeCustomized() {
		String[] envVars = new String[] { "var1=val1", "var2=val2" };
		KubernetesSchedulerProperties kubernetesSchedulerProperties = new KubernetesSchedulerProperties();
		kubernetesSchedulerProperties.setEnvironmentVariables(envVars);
		assertThat(kubernetesSchedulerProperties.getEnvironmentVariables()).as("Environment variables should not be null").isNotNull();
		assertEquals(2,
				kubernetesSchedulerProperties.getEnvironmentVariables().length,
				"Unexpected number of environment variables");
	}

	@Test
	public void testTaskServiceAccountNameDefault() {
		KubernetesSchedulerProperties kubernetesSchedulerProperties = new KubernetesSchedulerProperties();
		assertThat(kubernetesSchedulerProperties.getTaskServiceAccountName()).as("Task service account name should not be null").isNotNull();
		assertEquals(KubernetesSchedulerProperties.DEFAULT_TASK_SERVICE_ACCOUNT_NAME,
				kubernetesSchedulerProperties.getTaskServiceAccountName(),
				"Unexpected default task service account name");
	}

	@Test
	public void testTaskServiceAccountNameCanBeCustomized() {
		String taskServiceAccountName = "mysa";
		KubernetesSchedulerProperties kubernetesSchedulerProperties = new KubernetesSchedulerProperties();
		kubernetesSchedulerProperties.setTaskServiceAccountName(taskServiceAccountName);
		assertThat(kubernetesSchedulerProperties.getTaskServiceAccountName()).as("Task service account name should not be null").isNotNull();
		assertEquals(taskServiceAccountName,
				kubernetesSchedulerProperties.getTaskServiceAccountName(),
				"Unexpected task service account name");
	}

	// Re-implement when we have a proper env binding via boot
	// @RunWith(PowerMockRunner.class)
	// @PrepareForTest({ KubernetesSchedulerProperties.class })
	// public static class EnvTests {
	// 	@Test
	// 	public void testNamespaceFromEnvironment() throws Exception {
	// 		PowerMockito.mockStatic(System.class);
	// 		PowerMockito.when(System.getenv(KubernetesSchedulerProperties.ENV_KEY_KUBERNETES_NAMESPACE))
	// 				.thenReturn("nsfromenv");
	// 		KubernetesSchedulerProperties kubernetesSchedulerProperties = new KubernetesSchedulerProperties();
	// 		assertTrue("Namespace should not be empty or null",
	// 				StringUtils.hasText(kubernetesSchedulerProperties.getNamespace()));
	// 		assertEquals("Unexpected namespace from environment", "nsfromenv",
	// 				kubernetesSchedulerProperties.getNamespace());
	// 	}
	// }
}
