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

import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ilayaperumal Gopinathan
 * @author Chris Schaefer
 */
public class KubernetesConfigurationPropertiesTests {

	@Test
	public void testFabric8Namespacing() {
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		kubernetesDeployerProperties.getFabric8().setTrustCerts(true);
		kubernetesDeployerProperties.getFabric8().setMasterUrl("http://localhost:8090");
		// this can be set programatically in properties as well as an environment variable
		// (ie: CI, cmd line, etc) so ensure we have a clean slate here
		kubernetesDeployerProperties.setNamespace(null);
		kubernetesDeployerProperties.getFabric8().setNamespace("testing");

		KubernetesClient kubernetesClient = KubernetesClientFactory
				.getKubernetesClient(kubernetesDeployerProperties);

		assertThat(kubernetesClient.getMasterUrl().toString()).isEqualTo("http://localhost:8090");
		assertThat(kubernetesClient.getNamespace()).isEqualTo("testing");
		assertThat(kubernetesClient.getConfiguration().getMasterUrl()).isEqualTo("http://localhost:8090");
		assertThat(kubernetesClient.getConfiguration().isTrustCerts()).isTrue();
	}

	@Test
	public void testTopLevelNamespacing() {
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		kubernetesDeployerProperties.getFabric8().setTrustCerts(true);
		kubernetesDeployerProperties.getFabric8().setMasterUrl("http://localhost:8090");
		kubernetesDeployerProperties.setNamespace("toplevel");

		KubernetesClient kubernetesClient = KubernetesClientFactory
				.getKubernetesClient(kubernetesDeployerProperties);

		assertThat(kubernetesClient.getMasterUrl().toString()).isEqualTo("http://localhost:8090");
		assertThat(kubernetesClient.getNamespace()).isEqualTo("toplevel");
		assertThat(kubernetesClient.getConfiguration().getMasterUrl()).isEqualTo("http://localhost:8090");
		assertThat(kubernetesClient.getConfiguration().isTrustCerts()).isTrue();
	}

	@Test
	public void testTopLevelNamespacingOverride() {
		KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
		kubernetesDeployerProperties.getFabric8().setTrustCerts(true);
		kubernetesDeployerProperties.getFabric8().setMasterUrl("http://localhost:8090");
		kubernetesDeployerProperties.getFabric8().setNamespace("toplevel");
		kubernetesDeployerProperties.setNamespace("toplevel");

		KubernetesClient kubernetesClient = KubernetesClientFactory
				.getKubernetesClient(kubernetesDeployerProperties);

		assertThat(kubernetesClient.getMasterUrl().toString()).isEqualTo("http://localhost:8090");
		assertThat(kubernetesClient.getNamespace()).isEqualTo("toplevel");
		assertThat(kubernetesClient.getConfiguration().getMasterUrl()).isEqualTo("http://localhost:8090");
		assertThat(kubernetesClient.getConfiguration().isTrustCerts()).isTrue();
	}
}
