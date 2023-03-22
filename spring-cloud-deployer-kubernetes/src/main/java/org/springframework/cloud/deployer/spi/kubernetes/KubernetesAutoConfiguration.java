/*
 * Copyright 2015-2020 the original author or authors.
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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Spring Bean configuration for the {@link KubernetesAppDeployer}.
 *
 * @author Florian Rosenberg
 * @author Thomas Risberg
 * @author Ilayaperumal Gopinathan
 * @author Chris Schaefer
 */
@Configuration
@EnableConfigurationProperties({KubernetesDeployerProperties.class, KubernetesTaskLauncherProperties.class})
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
public class KubernetesAutoConfiguration {
	
	@Autowired
	private KubernetesDeployerProperties deployerProperties;

	@Autowired
	private KubernetesTaskLauncherProperties taskLauncherProperties;

	@Bean
	@ConditionalOnMissingBean(AppDeployer.class)
	public AppDeployer appDeployer(KubernetesClient kubernetesClient,
	                               ContainerFactory containerFactory) {
		return new KubernetesAppDeployer(deployerProperties, kubernetesClient, containerFactory);
	}

	@Bean
	@ConditionalOnMissingBean(TaskLauncher.class)
	public TaskLauncher taskDeployer(KubernetesClient kubernetesClient,
	                                 ContainerFactory containerFactory) {
		return new KubernetesTaskLauncher(deployerProperties, taskLauncherProperties, kubernetesClient, containerFactory);
	}

	@Bean
	@ConditionalOnMissingBean(KubernetesClient.class)
	public KubernetesClient kubernetesClient() {
		return KubernetesClientFactory.getKubernetesClient(this.deployerProperties);
	}

	@Bean
	public ContainerFactory containerFactory() {
		return new DefaultContainerFactory(deployerProperties);
	}

}
