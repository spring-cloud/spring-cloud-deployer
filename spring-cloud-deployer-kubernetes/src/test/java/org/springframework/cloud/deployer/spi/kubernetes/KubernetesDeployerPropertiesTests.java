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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link KubernetesDeployerProperties}.
 *
 * @author Glenn Renfro
 */
public class KubernetesDeployerPropertiesTests {

	@Test
	public void testImagePullPolicyDefault() {
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		assertNotNull("Image pull policy should not be null", kubernetesDeployerProperties.getImagePullPolicy());
		assertEquals("Invalid default image pull policy", ImagePullPolicy.IfNotPresent,
				kubernetesDeployerProperties.getImagePullPolicy());
	}

	@Test
	public void testImagePullPolicyCanBeCustomized() {
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		kubernetesDeployerProperties.setImagePullPolicy(ImagePullPolicy.Never);
		assertNotNull("Image pull policy should not be null", kubernetesDeployerProperties.getImagePullPolicy());
		assertEquals("Unexpected image pull policy", ImagePullPolicy.Never,
				kubernetesDeployerProperties.getImagePullPolicy());
	}

	@Test
	public void testRestartPolicyDefault() {
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		assertNotNull("Restart policy should not be null", kubernetesDeployerProperties.getRestartPolicy());
		assertEquals("Invalid default restart policy", RestartPolicy.Always,
				kubernetesDeployerProperties.getRestartPolicy());
	}

	@Test
	public void testRestartPolicyCanBeCustomized() {
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		kubernetesDeployerProperties.setRestartPolicy(RestartPolicy.OnFailure);
		assertNotNull("Restart policy should not be null", kubernetesDeployerProperties.getRestartPolicy());
		assertEquals("Unexpected restart policy", RestartPolicy.OnFailure,
				kubernetesDeployerProperties.getRestartPolicy());
	}

	@Test
	public void testEntryPointStyleDefault() {
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		assertNotNull("Entry point style should not be null", kubernetesDeployerProperties.getEntryPointStyle());
		assertEquals("Invalid default entry point style", EntryPointStyle.exec,
				kubernetesDeployerProperties.getEntryPointStyle());
	}

	@Test
	public void testEntryPointStyleCanBeCustomized() {
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		kubernetesDeployerProperties.setEntryPointStyle(EntryPointStyle.shell);
		assertNotNull("Entry point style should not be null", kubernetesDeployerProperties.getEntryPointStyle());
		assertEquals("Unexpected entry point stype", EntryPointStyle.shell,
				kubernetesDeployerProperties.getEntryPointStyle());
	}

	@Test
	public void testNamespaceDefault() {
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		if (kubernetesDeployerProperties.getNamespace() == null) {
			kubernetesDeployerProperties.setNamespace("default");

			assertTrue("Namespace should not be empty or null",
					StringUtils.hasText(kubernetesDeployerProperties.getNamespace()));
			assertEquals("Invalid default namespace", "default", kubernetesDeployerProperties.getNamespace());
		}
	}

	@Test
	public void testNamespaceCanBeCustomized() {
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		kubernetesDeployerProperties.setNamespace("myns");
		assertTrue("Namespace should not be empty or null",
				StringUtils.hasText(kubernetesDeployerProperties.getNamespace()));
		assertEquals("Unexpected namespace", "myns", kubernetesDeployerProperties.getNamespace());
	}

	@Test
	public void testImagePullSecretDefault() {
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		assertNull("No default image pull secret should be set", kubernetesDeployerProperties.getImagePullSecret());
	}

	@Test
	public void testImagePullSecretCanBeCustomized() {
		String secret = "mysecret";
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		kubernetesDeployerProperties.setImagePullSecret(secret);
		assertNotNull("Image pull secret should not be null", kubernetesDeployerProperties.getImagePullSecret());
		assertEquals("Unexpected image pull secret", secret, kubernetesDeployerProperties.getImagePullSecret());
	}

	@Test
	public void testEnvironmentVariablesDefault() {
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		assertEquals("No default environment variables should be set", 0,
				kubernetesDeployerProperties.getEnvironmentVariables().length);
	}

	@Test
	public void testEnvironmentVariablesCanBeCustomized() {
		String[] envVars = new String[] { "var1=val1", "var2=val2" };
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		kubernetesDeployerProperties.setEnvironmentVariables(envVars);
		assertNotNull("Environment variables should not be null",
				kubernetesDeployerProperties.getEnvironmentVariables());
		assertEquals("Unexpected number of environment variables", 2,
				kubernetesDeployerProperties.getEnvironmentVariables().length);
	}

	@Test
	public void testTaskServiceAccountNameDefault() {
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		assertNotNull("Task service account name should not be null",
				kubernetesDeployerProperties.getTaskServiceAccountName());
		assertEquals("Unexpected default task service account name",
				kubernetesDeployerProperties.DEFAULT_TASK_SERVICE_ACCOUNT_NAME,
				kubernetesDeployerProperties.getTaskServiceAccountName());
	}

	@Test
	public void testTaskServiceAccountNameCanBeCustomized() {
		String taskServiceAccountName = "mysa";
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		kubernetesDeployerProperties.setTaskServiceAccountName(taskServiceAccountName);
		assertNotNull("Task service account name should not be null",
				kubernetesDeployerProperties.getTaskServiceAccountName());
		assertEquals("Unexpected task service account name", taskServiceAccountName,
				kubernetesDeployerProperties.getTaskServiceAccountName());
	}
}
