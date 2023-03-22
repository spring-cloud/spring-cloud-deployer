/*
 * Copyright 2016-2021 the original author or authors.
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Quantity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.core.io.FileSystemResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link AbstractKubernetesDeployer}.
 *
 * @author Moritz Schulze
 * @author Chris Schaefer
 * @author Ilayaperumal Gopinathan
 */
public class RunAbstractKubernetesDeployerTests {

	private AppDeploymentRequest deploymentRequest;
	private KubernetesDeployerProperties kubernetesDeployerProperties;
	private Map<String, String> deploymentProperties;
	private DeploymentPropertiesResolver deploymentPropertiesResolver;


	@BeforeEach
	public void setUp() throws Exception {
		this.deploymentProperties = new HashMap<>();
		this.deploymentRequest = new AppDeploymentRequest(new AppDefinition("foo", Collections.emptyMap()), new FileSystemResource(""), deploymentProperties);
		this.kubernetesDeployerProperties = new KubernetesDeployerProperties();
		this.deploymentPropertiesResolver = new DeploymentPropertiesResolver(
				KubernetesDeployerProperties.KUBERNETES_DEPLOYER_PROPERTIES_PREFIX, this.kubernetesDeployerProperties);
	}

	@Test
	public void deduceImagePullPolicy_fallsBackToIfNotPresentIfOverrideNotParseable() {
		deploymentProperties.put("spring.cloud.deployer.kubernetes.imagePullPolicy", "not-a-real-value");
		ImagePullPolicy pullPolicy = this.deploymentPropertiesResolver.deduceImagePullPolicy(deploymentRequest.getDeploymentProperties());
		assertThat(pullPolicy).isEqualTo(ImagePullPolicy.IfNotPresent);
	}

	@Test
	public void limitGpu_noDeploymentProperty_incompleteServerProperty1_noGpu() {
		kubernetesDeployerProperties.getLimits().setGpuVendor("nvidia.com");
		Map<String, Quantity> limits = this.deploymentPropertiesResolver.deduceResourceLimits(deploymentRequest.getDeploymentProperties());
		assertThat(limits.get("nvidia.com/gpu")).isNull();
	}

	@Test
	public void limitGpu_noDeploymentProperty_incompleteServerProperty2_noGpu() {
		kubernetesDeployerProperties.getLimits().setGpuCount("2");
		Map<String, Quantity> limits = this.deploymentPropertiesResolver.deduceResourceLimits(deploymentRequest.getDeploymentProperties());
		assertThat(limits.get("nvidia.com/gpu")).isNull();
	}

	@Test
	public void limitGpu_noDeploymentProperty_serverProperty_usesServerProperty() {
		kubernetesDeployerProperties.getLimits().setGpuVendor("nvidia.com");
		kubernetesDeployerProperties.getLimits().setGpuCount("2");
		Map<String, Quantity> limits = this.deploymentPropertiesResolver.deduceResourceLimits(deploymentRequest.getDeploymentProperties());
		assertThat(limits.get("nvidia.com/gpu")).isEqualTo(new Quantity("2"));
	}

	@Test
	public void limitGpu_deploymentPropertyVendor_usesDeploymentProperty() {
		kubernetesDeployerProperties.getLimits().setGpuVendor("nvidia.com");
		kubernetesDeployerProperties.getLimits().setGpuCount("2");
		deploymentProperties.put("spring.cloud.deployer.kubernetes.limits.gpu_vendor", "ati.com");
		Map<String, Quantity> limits = this.deploymentPropertiesResolver.deduceResourceLimits(deploymentRequest.getDeploymentProperties());
		assertThat(limits.get("ati.com/gpu")).isEqualTo(new Quantity("2"));
	}

	@Test
	public void limitGpu_deploymentPropertyCount_usesDeploymentProperty() {
		kubernetesDeployerProperties.getLimits().setGpuVendor("nvidia.com");
		kubernetesDeployerProperties.getLimits().setGpuCount("2");
		deploymentProperties.put("spring.cloud.deployer.kubernetes.limits.gpu_count", "1");
		Map<String, Quantity> limits = this.deploymentPropertiesResolver.deduceResourceLimits(deploymentRequest.getDeploymentProperties());
		assertThat(limits.get("nvidia.com/gpu")).isEqualTo(new Quantity("1"));
	}

	@Test
	public void limitGpu_deploymentPropertyBoth_usesDeploymentProperty() {
		kubernetesDeployerProperties.getLimits().setGpuVendor("nvidia.com");
		kubernetesDeployerProperties.getLimits().setGpuCount("2");
		deploymentProperties.put("spring.cloud.deployer.kubernetes.limits.gpu_vendor", "ati.com");
		deploymentProperties.put("spring.cloud.deployer.kubernetes.limits.gpu_count", "1");
		Map<String, Quantity> limits = this.deploymentPropertiesResolver.deduceResourceLimits(deploymentRequest.getDeploymentProperties());
		assertThat(limits.get("ati.com/gpu")).isEqualTo(new Quantity("1"));
	}

	@Test
	public void limitCpu_noDeploymentProperty_serverProperty_usesServerProperty() {
		kubernetesDeployerProperties.getLimits().setCpu("400m");
		Map<String, Quantity> limits = this.deploymentPropertiesResolver.deduceResourceLimits(deploymentRequest.getDeploymentProperties());
		assertThat(limits.get("cpu")).isEqualTo(new Quantity("400m"));
	}

	@Test
	public void limitMemory_noDeploymentProperty_serverProperty_usesServerProperty() {
		kubernetesDeployerProperties.getLimits().setMemory("540Mi");
		Map<String, Quantity> limits = this.deploymentPropertiesResolver.deduceResourceLimits(deploymentRequest.getDeploymentProperties());
		assertThat(limits.get("memory")).isEqualTo(new Quantity("540Mi"));
	}

	@Test
	public void limitCpu_deploymentProperty_usesDeploymentProperty() {
		kubernetesDeployerProperties.getLimits().setCpu("100m");
		deploymentProperties.put("spring.cloud.deployer.kubernetes.limits.cpu", "400m");
		Map<String, Quantity> limits = this.deploymentPropertiesResolver.deduceResourceLimits(deploymentRequest.getDeploymentProperties());
		assertThat(limits.get("cpu")).isEqualTo(new Quantity("400m"));
	}

	@Test
	public void limitMemory_deploymentProperty_usesDeploymentProperty() {
		kubernetesDeployerProperties.getLimits().setMemory("640Mi");
		deploymentProperties.put("spring.cloud.deployer.kubernetes.limits.memory", "256Mi");
		Map<String, Quantity> limits = this.deploymentPropertiesResolver.deduceResourceLimits(deploymentRequest.getDeploymentProperties());
		assertThat(limits.get("memory")).isEqualTo(new Quantity("256Mi"));
	}

	@Test
	public void requestCpu_noDeploymentProperty_serverProperty_usesServerProperty() {
		kubernetesDeployerProperties.getRequests().setCpu("400m");
		Map<String, Quantity> requests = this.deploymentPropertiesResolver.deduceResourceRequests(deploymentRequest.getDeploymentProperties());
		assertThat(requests.get("cpu")).isEqualTo(new Quantity("400m"));
	}

	@Test
	public void requestMemory_noDeploymentProperty_serverProperty_usesServerProperty() {
		kubernetesDeployerProperties.getRequests().setMemory("120Mi");
		Map<String, Quantity> requests = this.deploymentPropertiesResolver.deduceResourceRequests(deploymentRequest.getDeploymentProperties());
		assertThat(requests.get("memory")).isEqualTo(new Quantity("120Mi"));
	}

	@Test
	public void requestCpu_deploymentProperty_usesDeploymentProperty() {
		kubernetesDeployerProperties.getRequests().setCpu("1000m");
		deploymentProperties.put("spring.cloud.deployer.kubernetes.requests.cpu", "461m");
		Map<String, Quantity> requests = this.deploymentPropertiesResolver.deduceResourceRequests(deploymentRequest.getDeploymentProperties());
		assertThat(requests.get("cpu")).isEqualTo(new Quantity("461m"));
	}

	@Test
	public void requestMemory_deploymentProperty_usesDeploymentProperty() {
		kubernetesDeployerProperties.getRequests().setMemory("640Mi");
		deploymentProperties.put("spring.cloud.deployer.kubernetes.requests.memory", "256Mi");
		Map<String, Quantity> requests = this.deploymentPropertiesResolver.deduceResourceRequests(deploymentRequest.getDeploymentProperties());
		assertThat(requests.get("memory")).isEqualTo(new Quantity("256Mi"));
	}
}
