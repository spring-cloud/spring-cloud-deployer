/*
 * Copyright 2015-2023 the original author or authors.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.AffinityBuilder;
import io.fabric8.kubernetes.api.model.Capabilities;
import io.fabric8.kubernetes.api.model.ConfigMapEnvSourceBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapKeySelector;
import io.fabric8.kubernetes.api.model.ConfigMapVolumeSource;
import io.fabric8.kubernetes.api.model.ConfigMapVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvFromSource;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.HostPathVolumeSource;
import io.fabric8.kubernetes.api.model.HostPathVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.KeyToPath;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.LabelSelectorRequirementBuilder;
import io.fabric8.kubernetes.api.model.NodeAffinity;
import io.fabric8.kubernetes.api.model.NodeSelectorRequirementBuilder;
import io.fabric8.kubernetes.api.model.NodeSelectorTerm;
import io.fabric8.kubernetes.api.model.ObjectFieldSelectorBuilder;
import io.fabric8.kubernetes.api.model.PodAffinity;
import io.fabric8.kubernetes.api.model.PodAffinityTerm;
import io.fabric8.kubernetes.api.model.PodAntiAffinity;
import io.fabric8.kubernetes.api.model.PodSecurityContext;
import io.fabric8.kubernetes.api.model.PodSecurityContextBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PreferredSchedulingTerm;
import io.fabric8.kubernetes.api.model.SELinuxOptions;
import io.fabric8.kubernetes.api.model.SeccompProfile;
import io.fabric8.kubernetes.api.model.SecretEnvSourceBuilder;
import io.fabric8.kubernetes.api.model.SecretKeySelector;
import io.fabric8.kubernetes.api.model.SecurityContext;
import io.fabric8.kubernetes.api.model.SecurityContextBuilder;
import io.fabric8.kubernetes.api.model.Sysctl;
import io.fabric8.kubernetes.api.model.Toleration;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.WeightedPodAffinityTerm;
import io.fabric8.kubernetes.api.model.WindowsSecurityContextOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.cloud.deployer.resource.docker.DockerResource;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

/**
 * Unit tests for {@link KubernetesAppDeployer}
 *
 * @author Donovan Muller
 * @author David Turanski
 * @author Ilayaperumal Gopinathan
 * @author Chris Schaefer
 * @author Enrique Medina Montenegro
 * @author Chris Bono
 * @author Corneil du Plessis
 */
@DisplayName("KubernetesAppDeployer")
public class KubernetesAppDeployerTests {

    private KubernetesAppDeployer deployer;

    private DeploymentPropertiesResolver deploymentPropertiesResolver = new DeploymentPropertiesResolver(
            KubernetesDeployerProperties.KUBERNETES_DEPLOYER_PROPERTIES_PREFIX, new KubernetesDeployerProperties());

    @Test
    public void deployWithVolumesOnly() throws Exception {
        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(),
                new HashMap<>());

        deployer = k8sAppDeployer(bindDeployerProperties());
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        assertThat(podSpec.getVolumes()).isEmpty();
    }

    @Test
    public void deployWithVolumesAndVolumeMounts() throws Exception {
        AppDefinition definition = new AppDefinition("app-test", null);
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.volumeMounts", "[" + "{name: 'testpvc', mountPath: '/test/pvc'}, "
                + "{name: 'testnfs', mountPath: '/test/nfs', readOnly: 'true'}" + "]");
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        deployer = k8sAppDeployer();
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        assertThat(podSpec.getVolumes()).containsOnly(
                // volume 'testhostpath' defined in dataflow-server.yml should not be added
                // as there is no corresponding volume mount
                new VolumeBuilder().withName("testpvc").withNewPersistentVolumeClaim("testClaim", true).build(),
                new VolumeBuilder().withName("testnfs").withNewNfs("/test/nfs", null, "10.0.0.1:111").build());

        props.clear();
        props.put("spring.cloud.deployer.kubernetes.volumes",
                "[" + "{name: testhostpath, hostPath: { path: '/test/override/hostPath' }},"
                        + "{name: 'testnfs', nfs: { server: '192.168.1.1:111', path: '/test/override/nfs' }} " + "]");
        props.put("spring.cloud.deployer.kubernetes.volumeMounts",
                "[" + "{name: 'testhostpath', mountPath: '/test/hostPath'}, "
                        + "{name: 'testpvc', mountPath: '/test/pvc'}, "
                        + "{name: 'testnfs', mountPath: '/test/nfs', readOnly: 'true'}" + "]");
        appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        deployer = k8sAppDeployer();
        podSpec = deployer.createPodSpec(appDeploymentRequest);

        HostPathVolumeSource hostPathVolumeSource = new HostPathVolumeSourceBuilder()
                .withPath("/test/override/hostPath").build();

        assertThat(podSpec.getVolumes()).containsOnly(
                new VolumeBuilder().withName("testhostpath").withHostPath(hostPathVolumeSource).build(),
                new VolumeBuilder().withName("testpvc").withNewPersistentVolumeClaim("testClaim", true).build(),
                new VolumeBuilder().withName("testnfs").withNewNfs("/test/override/nfs", null, "192.168.1.1:111").build());
    }
    @Test
    public void deployWithVolumesAndVolumeMountsOnAdditionalContainer() throws Exception {
        AppDefinition definition = new AppDefinition("app-test", null);
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.volumes", "[{name: 'config', configMap: {name: promtail-config, items: [{key: promtail.yaml, path: promtail.yaml}]}},{name: 'config2', configMap: {name: promtail-config, items: [{key: promtail.yaml, path: promtail.yaml}]}}]");
        props.put("spring.cloud.deployer.kubernetes.additional-containers", "[{name: 'promtail',image: image-path-of-promtail, ports:[{protocol: TCP,containerPort: 8080}],args: [\"-config.file=/home/conf/promtail.yaml\"],volumeMounts: [{name: 'config', mountPath: '/home/conf'}]},{name: 'promtail2',image: image-path-of-promtail, ports:[{protocol: TCP,containerPort: 8080}],args: [\"-config.file=/home/conf/promtail.yaml\"],volumeMounts: [{name: 'config2', mountPath: '/home/conf'}]}]");
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        deployer = k8sAppDeployer();
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        ConfigMapVolumeSource configMapVolumeSource = new ConfigMapVolumeSourceBuilder()
                .withName("promtail-config")
                .withItems(new KeyToPath("promtail.yaml", null, "promtail.yaml"))
                .build();
        Volume volume = new VolumeBuilder().withName("config").withNewConfigMapLike(configMapVolumeSource).endConfigMap().build();
        Volume volume2 = new VolumeBuilder().withName("config2").withNewConfigMapLike(configMapVolumeSource).endConfigMap().build();
        assertThat(podSpec.getVolumes()).containsExactly(volume, volume2);
    }
    @Test
    public void deployWithVolumesAndVolumeMountsOnAdditionalContainerAbsentVolumeMount() throws Exception {
        AppDefinition definition = new AppDefinition("app-test", null);
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.volumes", "[{name: 'config', configMap: {name: promtail-config, items: [{key: promtail.yaml, path: promtail.yaml}]}},{name: 'config2', configMap: {name: promtail-config, items: [{key: promtail.yaml, path: promtail.yaml}]}}]");
        // both containers reference config.
        props.put("spring.cloud.deployer.kubernetes.additional-containers", "[{name: 'promtail',image: image-path-of-promtail, ports:[{protocol: TCP,containerPort: 8080}],args: [\"-config.file=/home/conf/promtail.yaml\"],volumeMounts: [{name: 'config', mountPath: '/home/conf'}]},{name: 'promtail2',image: image-path-of-promtail, ports:[{protocol: TCP,containerPort: 8080}],args: [\"-config.file=/home/conf/promtail.yaml\"],volumeMounts: [{name: 'config', mountPath: '/home/conf'}]}]");
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        deployer = k8sAppDeployer();
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        ConfigMapVolumeSource configMapVolumeSource = new ConfigMapVolumeSourceBuilder()
                .withName("promtail-config")
                .withItems(new KeyToPath("promtail.yaml", null, "promtail.yaml"))
                .build();
        Set<String> volumeNames = podSpec.getVolumes().stream().map(v -> v.getName()).collect(Collectors.toSet());
        assertThat(volumeNames).doesNotContain("config2");
        Volume volume = new VolumeBuilder().withName("config").withNewConfigMapLike(configMapVolumeSource).endConfigMap().build();
        assertThat(podSpec.getVolumes()).containsExactly(volume);

    }
    @Test
    public void deployWithNodeSelectorGlobalProperty() throws Exception {
        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), null);

        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        kubernetesDeployerProperties.setNodeSelector("disktype:ssd, os:qnx");

        deployer = k8sAppDeployer(kubernetesDeployerProperties);
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        assertThat(podSpec.getNodeSelector()).containsOnly(entry("disktype", "ssd"), entry("os", "qnx"));
    }

    @Test
    public void deployWithNodeSelectorDeploymentProperty() throws Exception {
        AppDefinition definition = new AppDefinition("app-test", null);
        Map<String, String> props = new HashMap<>();
        props.put(KubernetesDeployerProperties.KUBERNETES_DEPLOYMENT_NODE_SELECTOR, "disktype:ssd, os: linux");
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        deployer = k8sAppDeployer();
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        assertThat(podSpec.getNodeSelector()).containsOnly(entry("disktype", "ssd"), entry("os", "linux"));
    }
    @Test
    public void deployWithNodeSelectorTrainTruckCaseProperty() throws Exception {
        AppDefinition definition = new AppDefinition("app-test", null);
        Map<String, String> props = new HashMap<>();
        props.put(KubernetesDeployerProperties.KUBERNETES_DEPLOYER_PROPERTIES_PREFIX + ".node-selector", "os: linux");
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        deployer = k8sAppDeployer();
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        assertThat(podSpec.getNodeSelector()).containsOnly(entry("os", "linux"));
    }

    @Test
    public void deployWithNodeSelectorDeploymentPropertyGlobalOverride() throws Exception {
        AppDefinition definition = new AppDefinition("app-test", null);
        Map<String, String> props = new HashMap<>();
        props.put(KubernetesDeployerProperties.KUBERNETES_DEPLOYMENT_NODE_SELECTOR, "disktype:ssd, os: openbsd");
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        kubernetesDeployerProperties.setNodeSelector("disktype:ssd, os:qnx");

        deployer = k8sAppDeployer(kubernetesDeployerProperties);
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        assertThat(podSpec.getNodeSelector()).containsOnly(entry("disktype", "ssd"), entry("os", "openbsd"));
    }

    @Test
    public void deployWithEnvironmentWithCommaDelimitedValue() throws Exception {
        AppDefinition definition = new AppDefinition("app-test", null);
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.environmentVariables",
                "JAVA_TOOL_OPTIONS='thing1,thing2',foo='bar,baz',car=caz,boo='zoo,gnu',doo=dar,OPTS='thing1'");

        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        deployer = k8sAppDeployer();
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        assertThat(podSpec.getContainers().get(0).getEnv())
                .contains(
                        new EnvVar("foo", "bar,baz", null),
                        new EnvVar("car", "caz", null),
                        new EnvVar("boo", "zoo,gnu", null),
                        new EnvVar("doo", "dar", null),
                        new EnvVar("JAVA_TOOL_OPTIONS", "thing1,thing2", null),
                        new EnvVar("OPTS", "thing1", null));
    }

    @Test
    public void deployWithEnvironmentWithSingleCommaDelimitedValue() throws Exception {
        AppDefinition definition = new AppDefinition("app-test", null);
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.environmentVariables",
                "JAVA_TOOL_OPTIONS='thing1,thing2'");

        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        deployer = k8sAppDeployer();
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        assertThat(podSpec.getContainers().get(0).getEnv())
                .contains(new EnvVar("JAVA_TOOL_OPTIONS", "thing1,thing2", null));
    }

    @Test
    public void deployWithEnvironmentWithMultipleCommaDelimitedValue() throws Exception {
        AppDefinition definition = new AppDefinition("app-test", null);
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.environmentVariables",
                "JAVA_TOOL_OPTIONS='thing1,thing2',OPTS='thing3, thing4'");

        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        deployer = k8sAppDeployer();
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        assertThat(podSpec.getContainers().get(0).getEnv())
                .contains(
                        new EnvVar("JAVA_TOOL_OPTIONS", "thing1,thing2", null),
                        new EnvVar("OPTS", "thing3, thing4", null));
    }

    @Test
    public void deployWithImagePullSecretDeploymentProperty() {
        AppDefinition definition = new AppDefinition("app-test", null);

        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.imagePullSecret", "regcred");

        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        deployer = k8sAppDeployer(new KubernetesDeployerProperties());
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        assertThat(podSpec.getImagePullSecrets().size()).isEqualTo(1);
        assertThat(podSpec.getImagePullSecrets().get(0).getName()).isEqualTo("regcred");
    }

    @Test
    public void deployWithImagePullSecretDeployerProperty() {
        AppDefinition definition = new AppDefinition("app-test", null);

        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), null);

        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        kubernetesDeployerProperties.setImagePullSecret("regcred");

        deployer = k8sAppDeployer(kubernetesDeployerProperties);
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        assertThat(podSpec.getImagePullSecrets().size()).isEqualTo(1);
        assertThat(podSpec.getImagePullSecrets().get(0).getName()).isEqualTo("regcred");
    }

    @Test
    public void deployWithImagePullSecretsDeploymentProperty() {
        AppDefinition definition = new AppDefinition("app-test", null);

        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.imagePullSecrets", "['regcredone','regcredtwo']");

        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        deployer = k8sAppDeployer(new KubernetesDeployerProperties());
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        assertThat(podSpec.getImagePullSecrets().size()).isEqualTo(2);
        assertThat(podSpec.getImagePullSecrets().get(0).getName()).isEqualTo("regcredone");
        assertThat(podSpec.getImagePullSecrets().get(1).getName()).isEqualTo("regcredtwo");
    }

    @Test
    public void deployWithImagePullSecretsDeployerProperty() {
        AppDefinition definition = new AppDefinition("app-test", null);

        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), null);

        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        kubernetesDeployerProperties.setImagePullSecrets(Arrays.asList("regcredone", "regcredtwo"));

        deployer = k8sAppDeployer(kubernetesDeployerProperties);
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        assertThat(podSpec.getImagePullSecrets().size()).isEqualTo(2);
        assertThat(podSpec.getImagePullSecrets().get(0).getName()).isEqualTo("regcredone");
        assertThat(podSpec.getImagePullSecrets().get(1).getName()).isEqualTo("regcredtwo");
    }

    @Test
    public void deployWithDeploymentServiceAccountNameDeploymentProperties() {
        AppDefinition definition = new AppDefinition("app-test", null);

        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.deploymentServiceAccountName", "myserviceaccount");

        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        deployer = k8sAppDeployer(new KubernetesDeployerProperties());
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        assertThat(podSpec.getServiceAccountName()).isNotNull();
        assertThat(podSpec.getServiceAccountName().equals("myserviceaccount"));
    }

    @Test
    public void deployWithDeploymentServiceAccountNameDeployerProperty() {
        AppDefinition definition = new AppDefinition("app-test", null);

        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), null);

        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        kubernetesDeployerProperties.setDeploymentServiceAccountName("myserviceaccount");

        deployer = k8sAppDeployer(kubernetesDeployerProperties);
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        assertThat(podSpec.getServiceAccountName()).isNotNull();
        assertThat(podSpec.getServiceAccountName().equals("myserviceaccount"));
    }

    @Test
    public void deployWithDeploymentServiceAccountNameDeploymentPropertyOverride() {
        AppDefinition definition = new AppDefinition("app-test", null);

        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.deploymentServiceAccountName", "overridesan");

        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        kubernetesDeployerProperties.setDeploymentServiceAccountName("defaultsan");

        deployer = k8sAppDeployer(kubernetesDeployerProperties);
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        assertThat(podSpec.getServiceAccountName()).isNotNull();
        assertThat(podSpec.getServiceAccountName().equals("overridesan"));
    }

    @Test
    public void deployWithTolerations() throws Exception {
        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(),
                new HashMap<>());

        deployer = k8sAppDeployer();
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        assertThat(podSpec.getTolerations()).isNotEmpty();
    }

    @Test
    public void deployWithGlobalTolerations() {
        AppDefinition definition = new AppDefinition("app-test", null);

        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.tolerations",
                "[{key: 'test', value: 'true', operator: 'Equal', effect: 'NoSchedule', tolerationSeconds: 5}, "
                        + "{key: 'test2', value: 'false', operator: 'Equal', effect: 'NoSchedule', tolerationSeconds: 5}]");

        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        deployer = k8sAppDeployer(new KubernetesDeployerProperties());
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        assertThat(podSpec.getTolerations()).isNotNull();
        assertThat(podSpec.getTolerations().size() == 2);
        assertThat(podSpec.getTolerations().contains(new Toleration("NoSchedule", "test", "Equal", 5L, "true")));
        assertThat(podSpec.getTolerations().contains(new Toleration("NoSchedule", "test2", "Equal", 5L, "false")));
    }

    @Test
    public void deployWithTolerationPropertyOverride() {
        AppDefinition definition = new AppDefinition("app-test", null);

        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.tolerations",
                "[{key: 'test', value: 'true', operator: 'Equal', effect: 'NoSchedule', tolerationSeconds: 5}, "
                        + "{key: 'test2', value: 'false', operator: 'Equal', effect: 'NoSchedule', tolerationSeconds: 5}]");

        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        KubernetesDeployerProperties.Toleration toleration = new KubernetesDeployerProperties.Toleration();
        toleration.setEffect("NoSchedule");
        toleration.setKey("test");
        toleration.setOperator("Equal");
        toleration.setTolerationSeconds(5L);
        toleration.setValue("false");

        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        kubernetesDeployerProperties.getTolerations().add(toleration);

        deployer = k8sAppDeployer(kubernetesDeployerProperties);
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        assertThat(podSpec.getTolerations()).isNotNull();
        assertThat(podSpec.getTolerations().size() == 2);
        assertThat(podSpec.getTolerations().contains(new Toleration("NoSchedule", "test", "Equal", 5L, "true")));
        assertThat(podSpec.getTolerations().contains(new Toleration("NoSchedule", "test2", "Equal", 5L, "false")));
    }

    @Test
    public void deployWithDuplicateTolerationKeyPropertyOverride() {
        AppDefinition definition = new AppDefinition("app-test", null);

        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.tolerations",
                "[{key: 'test', value: 'true', operator: 'Equal', effect: 'NoSchedule', tolerationSeconds: 5}]");

        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        KubernetesDeployerProperties.Toleration toleration = new KubernetesDeployerProperties.Toleration();
        toleration.setEffect("NoSchedule");
        toleration.setKey("test");
        toleration.setOperator("Equal");
        toleration.setTolerationSeconds(5L);
        toleration.setValue("false");

        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        kubernetesDeployerProperties.getTolerations().add(toleration);
        kubernetesDeployerProperties.setStartupHttpProbePort(kubernetesDeployerProperties.getLivenessHttpProbePort());
        deployer = k8sAppDeployer(kubernetesDeployerProperties);
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        assertThat(podSpec.getTolerations()).isNotNull();
        assertThat(podSpec.getTolerations().size() == 1);
        assertThat(podSpec.getTolerations().contains(new Toleration("NoSchedule", "test2", "Equal", 5L, "false")));
    }

    @Test
    public void deployWithDuplicateGlobalToleration() {
        AppDefinition definition = new AppDefinition("app-test", null);

        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), null);

        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();

        KubernetesDeployerProperties.Toleration toleration1 = new KubernetesDeployerProperties.Toleration();
        toleration1.setEffect("NoSchedule");
        toleration1.setKey("test");
        toleration1.setOperator("Equal");
        toleration1.setTolerationSeconds(5L);
        toleration1.setValue("false");

        kubernetesDeployerProperties.getTolerations().add(toleration1);

        KubernetesDeployerProperties.Toleration toleration2 = new KubernetesDeployerProperties.Toleration();
        toleration2.setEffect("NoSchedule");
        toleration2.setKey("test");
        toleration2.setOperator("Equal");
        toleration2.setTolerationSeconds(5L);
        toleration2.setValue("true");

        kubernetesDeployerProperties.getTolerations().add(toleration2);

        deployer = k8sAppDeployer(kubernetesDeployerProperties);
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        assertThat(podSpec.getTolerations()).isNotNull();
        assertThat(podSpec.getTolerations().size() == 1);
        assertThat(podSpec.getTolerations().contains(new Toleration("NoSchedule", "test2", "Equal", 5L, "true")));
    }

    @Test
    public void testInvalidDeploymentLabelDelimiter() {
        Map<String, String> props = Collections.singletonMap("spring.cloud.deployer.kubernetes.deploymentLabels",
                "label1|value1");

        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        assertThatThrownBy(() -> {
            this.deploymentPropertiesResolver.getDeploymentLabels(appDeploymentRequest.getDeploymentProperties());
        }).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testInvalidMultipleDeploymentLabelDelimiter() {
        Map<String, String> props = Collections.singletonMap("spring.cloud.deployer.kubernetes.deploymentLabels",
                "label1:value1 label2:value2");

        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        assertThatThrownBy(() -> {
            this.deploymentPropertiesResolver.getDeploymentLabels(appDeploymentRequest.getDeploymentProperties());
        }).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testDeploymentLabels() {
        Map<String, String> props = Collections.singletonMap("spring.cloud.deployer.kubernetes.deploymentLabels",
                "label1:value1,label2:value2");

        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        Map<String, String> deploymentLabels = this.deploymentPropertiesResolver.getDeploymentLabels(appDeploymentRequest.getDeploymentProperties());

        assertThat(deploymentLabels).isNotEmpty();
        assertThat(deploymentLabels.size()).as("Invalid number of labels").isEqualTo(2);
        assertThat(deploymentLabels).containsKey("label1");
        assertThat(deploymentLabels.get("label1")).as("Invalid value for 'label1'").isEqualTo("value1");
        assertThat(deploymentLabels).containsKey("label2");
        assertThat(deploymentLabels.get("label2")).as("Invalid value for 'label2'").isEqualTo("value2");
    }

    @Test
    public void testSecretKeyRef() {
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.secretKeyRefs",
                "[{envVarName: 'SECRET_PASSWORD', secretName: 'mySecret', dataKey: 'password'}]");

        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        deployer = k8sAppDeployer(new KubernetesDeployerProperties());
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        List<EnvVar> envVars = podSpec.getContainers().get(0).getEnv();

        assertThat(envVars.size()).as("Invalid number of env vars").isEqualTo(2);

        EnvVar secretKeyRefEnvVar = envVars.get(0);
        assertThat(secretKeyRefEnvVar.getName()).as("Unexpected env var name").isEqualTo("SECRET_PASSWORD");
        SecretKeySelector secretKeySelector = secretKeyRefEnvVar.getValueFrom().getSecretKeyRef();
        assertThat(secretKeySelector.getName()).as("Unexpected secret name").isEqualTo("mySecret");
        assertThat(secretKeySelector.getKey()).as("Unexpected secret data key").isEqualTo("password");
    }

    @Test
    public void testSecretKeyRefMultiple() {
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.secretKeyRefs",
                "[{envVarName: 'SECRET_PASSWORD', secretName: 'mySecret', dataKey: 'password'}," +
                        "{envVarName: 'SECRET_USERNAME', secretName: 'mySecret2', dataKey: 'username'}]");

        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        deployer = k8sAppDeployer(new KubernetesDeployerProperties());
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        List<EnvVar> envVars = podSpec.getContainers().get(0).getEnv();

        assertThat(envVars.size()).as("Invalid number of env vars").isEqualTo(3);

        EnvVar secretKeyRefEnvVar = envVars.get(0);
        assertThat(secretKeyRefEnvVar.getName()).as("Unexpected env var name").isEqualTo("SECRET_PASSWORD");
        SecretKeySelector secretKeySelector = secretKeyRefEnvVar.getValueFrom().getSecretKeyRef();
        assertThat(secretKeySelector.getName()).as("Unexpected secret name").isEqualTo("mySecret");
        assertThat(secretKeySelector.getKey()).as("Unexpected secret data key").isEqualTo("password");

        secretKeyRefEnvVar = envVars.get(1);
        assertThat(secretKeyRefEnvVar.getName()).as("Unexpected env var name").isEqualTo("SECRET_USERNAME");
        secretKeySelector = secretKeyRefEnvVar.getValueFrom().getSecretKeyRef();
        assertThat(secretKeySelector.getName()).as("Unexpected secret name").isEqualTo("mySecret2");
        assertThat(secretKeySelector.getKey()).as("Unexpected secret data key").isEqualTo("username");
    }

    @Test
    public void testSecretKeyRefGlobal() {
        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), null);

        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        KubernetesDeployerProperties.SecretKeyRef secretKeyRef = new KubernetesDeployerProperties.SecretKeyRef();
        secretKeyRef.setEnvVarName("SECRET_PASSWORD_GLOBAL");
        secretKeyRef.setSecretName("mySecretGlobal");
        secretKeyRef.setDataKey("passwordGlobal");
        kubernetesDeployerProperties.setSecretKeyRefs(Collections.singletonList(secretKeyRef));

        deployer = k8sAppDeployer(kubernetesDeployerProperties);
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        List<EnvVar> envVars = podSpec.getContainers().get(0).getEnv();

        assertThat(envVars.size()).as("Invalid number of env vars").isEqualTo(2);

        EnvVar secretKeyRefEnvVar = envVars.get(0);
        assertThat(secretKeyRefEnvVar.getName()).as("Unexpected env var name").isEqualTo("SECRET_PASSWORD_GLOBAL");
        SecretKeySelector secretKeySelector = secretKeyRefEnvVar.getValueFrom().getSecretKeyRef();
        assertThat(secretKeySelector.getName()).as("Unexpected secret name").isEqualTo("mySecretGlobal");
        assertThat(secretKeySelector.getKey()).as("Unexpected secret data key").isEqualTo("passwordGlobal");
    }

    @Test
    public void testSecretKeyRefPropertyOverride() {
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.secretKeyRefs",
                "[{envVarName: 'SECRET_PASSWORD_GLOBAL', secretName: 'mySecret', dataKey: 'password'}," +
                        "{envVarName: 'SECRET_USERNAME', secretName: 'mySecret2', dataKey: 'username'}]");

        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();

        List<KubernetesDeployerProperties.SecretKeyRef> globalSecretKeyRefs = new ArrayList<>();
        KubernetesDeployerProperties.SecretKeyRef globalSecretKeyRef1 = new KubernetesDeployerProperties.SecretKeyRef();
        globalSecretKeyRef1.setEnvVarName("SECRET_PASSWORD_GLOBAL");
        globalSecretKeyRef1.setSecretName("mySecretGlobal");
        globalSecretKeyRef1.setDataKey("passwordGlobal");

        KubernetesDeployerProperties.SecretKeyRef globalSecretKeyRef2 = new KubernetesDeployerProperties.SecretKeyRef();
        globalSecretKeyRef2.setEnvVarName("SECRET_USERNAME_GLOBAL");
        globalSecretKeyRef2.setSecretName("mySecretGlobal");
        globalSecretKeyRef2.setDataKey("usernameGlobal");

        globalSecretKeyRefs.add(globalSecretKeyRef1);
        globalSecretKeyRefs.add(globalSecretKeyRef2);

        kubernetesDeployerProperties.setSecretKeyRefs(globalSecretKeyRefs);

        deployer = k8sAppDeployer(kubernetesDeployerProperties);
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        List<EnvVar> envVars = podSpec.getContainers().get(0).getEnv();

        assertThat(envVars.size()).as("Invalid number of env vars").isEqualTo(4);

        // deploy prop overrides global
        EnvVar secretKeyRefEnvVar = envVars.get(0);
        assertThat(secretKeyRefEnvVar.getName()).as("Unexpected env var name").isEqualTo("SECRET_PASSWORD_GLOBAL");
        SecretKeySelector secretKeySelector = secretKeyRefEnvVar.getValueFrom().getSecretKeyRef();
        assertThat(secretKeySelector.getName()).as("Unexpected secret name").isEqualTo("mySecret");
        assertThat(secretKeySelector.getKey()).as("Unexpected secret data key").isEqualTo("password");

        // unique deploy prop
        secretKeyRefEnvVar = envVars.get(1);
        assertThat(secretKeyRefEnvVar.getName()).as("Unexpected env var name").isEqualTo("SECRET_USERNAME");
        secretKeySelector = secretKeyRefEnvVar.getValueFrom().getSecretKeyRef();
        assertThat(secretKeySelector.getName()).as("Unexpected secret name").isEqualTo("mySecret2");
        assertThat(secretKeySelector.getKey()).as("Unexpected secret data key").isEqualTo("username");

        // unique, non-overridden global prop
        secretKeyRefEnvVar = envVars.get(2);
        assertThat(secretKeyRefEnvVar.getName()).as("Unexpected env var name").isEqualTo("SECRET_USERNAME_GLOBAL");
        secretKeySelector = secretKeyRefEnvVar.getValueFrom().getSecretKeyRef();
        assertThat(secretKeySelector.getName()).as("Unexpected secret name").isEqualTo("mySecretGlobal");
        assertThat(secretKeySelector.getKey()).as("Unexpected secret data key").isEqualTo("usernameGlobal");
    }

    @Test
    public void testSecretKeyRefGlobalFromYaml() throws Exception {
        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), null);

        deployer = k8sAppDeployer();
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        List<EnvVar> envVars = podSpec.getContainers().get(0).getEnv();

        assertThat(envVars.size()).as("Invalid number of env vars").isEqualTo(3);

        EnvVar secretKeyRefEnvVar = envVars.get(0);
        assertThat(secretKeyRefEnvVar.getName()).as("Unexpected env var name").isEqualTo("SECRET_PASSWORD");
        SecretKeySelector secretKeySelector = secretKeyRefEnvVar.getValueFrom().getSecretKeyRef();
        assertThat(secretKeySelector.getName()).as("Unexpected secret name").isEqualTo("mySecret");
        assertThat(secretKeySelector.getKey()).as("Unexpected secret data key").isEqualTo("myPassword");
    }

    @Test
    public void testConfigMapKeyRef() {
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.configMapKeyRefs",
                "[{envVarName: 'MY_ENV', configMapName: 'myConfigMap', dataKey: 'envName'}]");

        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        deployer = k8sAppDeployer(new KubernetesDeployerProperties());
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        List<EnvVar> envVars = podSpec.getContainers().get(0).getEnv();

        assertThat(envVars.size()).as("Invalid number of env vars").isEqualTo(2);

        EnvVar configMapKeyRefEnvVar = envVars.get(0);
        assertThat(configMapKeyRefEnvVar.getName()).as("Unexpected env var name").isEqualTo("MY_ENV");
        ConfigMapKeySelector configMapKeySelector = configMapKeyRefEnvVar.getValueFrom().getConfigMapKeyRef();
        assertThat(configMapKeySelector.getName()).as("Unexpected config map name").isEqualTo("myConfigMap");
        assertThat(configMapKeySelector.getKey()).as("Unexpected config map data key").isEqualTo("envName");
    }

    @Test
    public void testConfigMapKeyRefMultiple() {
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.configMapKeyRefs",
                "[{envVarName: 'MY_ENV', configMapName: 'myConfigMap', dataKey: 'envName'}," +
                        "{envVarName: 'ENV_VALUES', configMapName: 'myOtherConfigMap', dataKey: 'diskType'}]");

        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        deployer = k8sAppDeployer(new KubernetesDeployerProperties());
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        List<EnvVar> envVars = podSpec.getContainers().get(0).getEnv();

        assertThat(envVars.size()).as("Invalid number of env vars").isEqualTo(3);

        EnvVar configMapKeyRefEnvVar = envVars.get(0);
        assertThat(configMapKeyRefEnvVar.getName()).as("Unexpected env var name").isEqualTo("MY_ENV");
        ConfigMapKeySelector configMapKeySelector = configMapKeyRefEnvVar.getValueFrom().getConfigMapKeyRef();
        assertThat(configMapKeySelector.getName()).as("Unexpected config map name").isEqualTo("myConfigMap");
        assertThat(configMapKeySelector.getKey()).as("Unexpected config map data key").isEqualTo("envName");

        configMapKeyRefEnvVar = envVars.get(1);
        assertThat(configMapKeyRefEnvVar.getName()).as("Unexpected env var name").isEqualTo("ENV_VALUES");
        configMapKeySelector = configMapKeyRefEnvVar.getValueFrom().getConfigMapKeyRef();
        assertThat(configMapKeySelector.getName()).as("Unexpected config map name").isEqualTo("myOtherConfigMap");
        assertThat(configMapKeySelector.getKey()).as("Unexpected config map data key").isEqualTo("diskType");
    }

    @Test
    public void testConfigMapKeyRefGlobal() {
        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), null);

        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        KubernetesDeployerProperties.ConfigMapKeyRef configMapKeyRef = new KubernetesDeployerProperties.ConfigMapKeyRef();
        configMapKeyRef.setEnvVarName("MY_ENV_GLOBAL");
        configMapKeyRef.setConfigMapName("myConfigMapGlobal");
        configMapKeyRef.setDataKey("envGlobal");
        kubernetesDeployerProperties.setConfigMapKeyRefs(Collections.singletonList(configMapKeyRef));

        deployer = k8sAppDeployer(kubernetesDeployerProperties);
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        List<EnvVar> envVars = podSpec.getContainers().get(0).getEnv();

        assertThat(envVars.size()).as("Invalid number of env vars").isEqualTo(2);

        EnvVar configMapKeyRefEnvVar = envVars.get(0);
        assertThat(configMapKeyRefEnvVar.getName()).as("Unexpected env var name").isEqualTo("MY_ENV_GLOBAL");
        ConfigMapKeySelector configMapKeySelector = configMapKeyRefEnvVar.getValueFrom().getConfigMapKeyRef();
        assertThat(configMapKeySelector.getName()).as("Unexpected config map name").isEqualTo("myConfigMapGlobal");
        assertThat(configMapKeySelector.getKey()).as("Unexpected config data key").isEqualTo("envGlobal");
    }

    @Test
    public void testConfigMapKeyRefPropertyOverride() {
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.configMapKeyRefs",
                "[{envVarName: 'MY_ENV', configMapName: 'myConfigMap', dataKey: 'envName'}," +
                        "{envVarName: 'ENV_VALUES', configMapName: 'myOtherConfigMap', dataKey: 'diskType'}]");

        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();

        List<KubernetesDeployerProperties.ConfigMapKeyRef> globalConfigMapKeyRefs = new ArrayList<>();
        KubernetesDeployerProperties.ConfigMapKeyRef globalConfigMapKeyRef1 = new KubernetesDeployerProperties.ConfigMapKeyRef();
        globalConfigMapKeyRef1.setEnvVarName("MY_ENV");
        globalConfigMapKeyRef1.setConfigMapName("myEnvGlobal");
        globalConfigMapKeyRef1.setDataKey("envGlobal");

        KubernetesDeployerProperties.ConfigMapKeyRef globalConfigMapKeyRef2 = new KubernetesDeployerProperties.ConfigMapKeyRef();
        globalConfigMapKeyRef2.setEnvVarName("MY_VALS_GLOBAL");
        globalConfigMapKeyRef2.setConfigMapName("myValsGlobal");
        globalConfigMapKeyRef2.setDataKey("valsGlobal");

        globalConfigMapKeyRefs.add(globalConfigMapKeyRef1);
        globalConfigMapKeyRefs.add(globalConfigMapKeyRef2);

        kubernetesDeployerProperties.setConfigMapKeyRefs(globalConfigMapKeyRefs);

        deployer = k8sAppDeployer(kubernetesDeployerProperties);
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        List<EnvVar> envVars = podSpec.getContainers().get(0).getEnv();

        assertThat(envVars.size()).as("Invalid number of env vars").isEqualTo(4);

        // deploy prop overrides global
        EnvVar configMapKeyRefEnvVar = envVars.get(0);
        assertThat(configMapKeyRefEnvVar.getName()).as("Unexpected env var name").isEqualTo("MY_ENV");
        ConfigMapKeySelector configMapKeySelector = configMapKeyRefEnvVar.getValueFrom().getConfigMapKeyRef();
        assertThat(configMapKeySelector.getName()).as("Unexpected config map name").isEqualTo("myConfigMap");
        assertThat(configMapKeySelector.getKey()).as("Unexpected config map data key").isEqualTo("envName");

        // unique deploy prop
        configMapKeyRefEnvVar = envVars.get(1);
        assertThat(configMapKeyRefEnvVar.getName()).as("Unexpected env var name").isEqualTo("ENV_VALUES");
        configMapKeySelector = configMapKeyRefEnvVar.getValueFrom().getConfigMapKeyRef();
        assertThat(configMapKeySelector.getName()).as("Unexpected config map name").isEqualTo("myOtherConfigMap");
        assertThat(configMapKeySelector.getKey()).as("Unexpected config map data key").isEqualTo("diskType");

        // unique, non-overridden global prop
        configMapKeyRefEnvVar = envVars.get(2);
        assertThat(configMapKeyRefEnvVar.getName()).as("Unexpected env var name").isEqualTo("MY_VALS_GLOBAL");
        configMapKeySelector = configMapKeyRefEnvVar.getValueFrom().getConfigMapKeyRef();
        assertThat(configMapKeySelector.getName()).as("Unexpected config map name").isEqualTo("myValsGlobal");
        assertThat(configMapKeySelector.getKey()).as("Unexpected config map data key").isEqualTo("valsGlobal");
    }

    @Test
    public void testConfigMapKeyRefGlobalFromYaml() throws Exception {
        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), null);

        deployer = k8sAppDeployer();
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        List<EnvVar> envVars = podSpec.getContainers().get(0).getEnv();

        assertThat(envVars.size()).as("Invalid number of env vars").isEqualTo(3);

        EnvVar configMapKeyRefEnvVar = envVars.get(1);
        assertThat(configMapKeyRefEnvVar.getName()).as("Unexpected env var name").isEqualTo("MY_ENV");
        ConfigMapKeySelector configMapKeySelector = configMapKeyRefEnvVar.getValueFrom().getConfigMapKeyRef();
        assertThat(configMapKeySelector.getName()).as("Unexpected config map name").isEqualTo("myConfigMap");
        assertThat(configMapKeySelector.getKey()).as("Unexpected config map data key").isEqualTo("envName");
    }
    @Test
    public void testInitContainerProperties() {
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.initContainer", "{ \"imageName\": \"busybox:1\", \"containerName\": \"bb_s1\", \"commands\": [\"sh\", \"-c\", \"script1.sh\"] }");

        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        deployer = k8sAppDeployer(new KubernetesDeployerProperties());
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);
        assertThat(podSpec.getInitContainers()).isNotEmpty();
        Container container = podSpec.getInitContainers().get(0);
        assertThat(container.getImage()).isEqualTo("busybox:1");
        assertThat(container.getName()).isEqualTo("bb_s1");
        assertThat(container.getCommand()).containsExactly("sh", "-c", "script1.sh");
    }
    @Test
    public void testInitContainerJsonArrayProperties() {
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.init-containers", "[{ \"imageName\": \"busybox:1\", \"containerName\": \"bb_s1\", \"commands\": [\"sh\", \"-c\", \"script1.sh\"] }]");

        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        deployer = k8sAppDeployer(new KubernetesDeployerProperties());
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);
        assertThat(podSpec.getInitContainers()).isNotEmpty();
        Container container = podSpec.getInitContainers().get(0);
        assertThat(container.getImage()).isEqualTo("busybox:1");
        assertThat(container.getName()).isEqualTo("bb_s1");
        assertThat(container.getCommand()).containsExactly("sh", "-c", "script1.sh");
    }

    @Test
    public void testInitContainerEnvironmentVariables() {
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.initContainers[0]", "{ \"imageName\": \"busybox:1\", \"environmentVariablesFromFieldRefs\": [\"POD_UID=metadata.uid\"] }");
        props.put("spring.cloud.deployer.kubernetes.initContainers[1]", "{ \"imageName\": \"busybox:2\", \"configMapRefEnvVars\": [\"myConfigMap\",\"theirMap\"], \"secretRefEnvVars\": [\"mySecret\"] }");

        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        deployer = k8sAppDeployer(new KubernetesDeployerProperties());
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);
        assertThat(podSpec.getInitContainers()).isNotEmpty();
        assertThat(podSpec.getInitContainers().size()).isEqualTo(2);
        Container container0 = podSpec.getInitContainers().get(0);
        assertThat(container0.getImage()).isEqualTo("busybox:1");
        assertThat(container0.getEnv().get(0).getName()).isEqualTo("POD_UID");
        assertThat(container0.getEnv().get(0).getValueFrom().getFieldRef().getFieldPath()).isEqualTo("metadata.uid");
        Container container1 = podSpec.getInitContainers().get(1);
        assertThat(container1.getImage()).isEqualTo("busybox:2");
        assertThat(container1.getEnvFrom().get(0).getConfigMapRef().getName()).isEqualTo("myConfigMap");
		assertThat(container1.getEnvFrom().get(1).getConfigMapRef().getName()).isEqualTo("theirMap");
        assertThat(container1.getEnvFrom().get(2).getSecretRef().getName()).isEqualTo("mySecret");
    }

    @Test
    public void testMultipleInitContainerProperties() {
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.initContainers[0]", "{ \"imageName\": \"busybox:1\", \"containerName\": \"bb_s1\", \"commands\": [\"sh\", \"-c\", \"script1.sh\"] }");
        props.put("spring.cloud.deployer.kubernetes.initContainers[1].imageName", "busybox:2");
        props.put("spring.cloud.deployer.kubernetes.initContainers[1].containerName", "bb_s2");
        props.put("spring.cloud.deployer.kubernetes.initContainers[1].commands", "sh,-c,script2.sh");
		props.put("spring.cloud.deployer.kubernetes.initContainers[1].environmentVariables", "foo=baz");

		props.put("spring.cloud.deployer.kubernetes.initContainers[2]", "{ \"image\": \"busybox:3\", \"name\": \"bb_s3\", \"args\": [\"-c\", \"script3.sh\"], \"volumeMounts\": [{\"mountPath\": \"/data\", \"name\": \"s3vol\", \"readOnly\": true}] }");
		props.put("spring.cloud.deployer.kubernetes.initContainers[3].image", "busybox:2");
		props.put("spring.cloud.deployer.kubernetes.initContainers[3].name", "bb_s4");
		props.put("spring.cloud.deployer.kubernetes.initContainers[3].command", "sh,-c,script4.sh");
		props.put("spring.cloud.deployer.kubernetes.initContainers[3].env", "foo=bar");

		AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        deployer = k8sAppDeployer(new KubernetesDeployerProperties());
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);
        assertThat(podSpec.getInitContainers()).isNotEmpty();
        assertThat(podSpec.getInitContainers().size()).isEqualTo(4);
        Container container0 = podSpec.getInitContainers().get(0);
        assertThat(container0.getImage()).isEqualTo("busybox:1");
        assertThat(container0.getName()).isEqualTo("bb_s1");
        assertThat(container0.getCommand()).containsExactly("sh", "-c", "script1.sh");
        Container container1 = podSpec.getInitContainers().get(1);
        assertThat(container1.getImage()).isEqualTo("busybox:2");
        assertThat(container1.getName()).isEqualTo("bb_s2");
        assertThat(container1.getCommand()).containsExactly("sh", "-c", "script2.sh");
		assertThat(container1.getEnv()).isEqualTo(Collections.singletonList(new EnvVar("foo","baz", null)));
		Container container2 = podSpec.getInitContainers().get(2);
        assertThat(container2.getImage()).isEqualTo("busybox:3");
        assertThat(container2.getName()).isEqualTo("bb_s3");
        assertThat(container2.getArgs()).containsExactly("-c", "script3.sh");
        assertThat(container2.getVolumeMounts()).isNotEmpty();
        assertThat(container2.getVolumeMounts().get(0).getName()).isEqualTo("s3vol");
        assertThat(container2.getVolumeMounts().get(0).getMountPath()).isEqualTo("/data");
        assertThat(container2.getVolumeMounts().get(0).getReadOnly()).isTrue();
		Container container3 = podSpec.getInitContainers().get(3);
		assertThat(container3.getImage()).isEqualTo("busybox:2");
		assertThat(container3.getName()).isEqualTo("bb_s4");
		assertThat(container3.getCommand()).containsExactly("sh", "-c", "script4.sh");
		assertThat(container3.getEnv()).isEqualTo(Collections.singletonList(new EnvVar("foo","bar", null)));
	}

    @Test
    public void testNodeAffinityProperty() {
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.affinity.nodeAffinity",
                "{ requiredDuringSchedulingIgnoredDuringExecution:" +
                        "  { nodeSelectorTerms:" +
                        "    [ { matchExpressions:" +
                        "        [ { key: 'kubernetes.io/e2e-az-name', " +
                        "            operator: 'In'," +
                        "            values:" +
                        "            [ 'e2e-az1', 'e2e-az2']}]}]}, " +
                        "  preferredDuringSchedulingIgnoredDuringExecution:" +
                        "  [ { weight: 1," +
                        "      preference:" +
                        "      { matchExpressions:" +
                        "        [ { key: 'another-node-label-key'," +
                        "            operator: 'In'," +
                        "            values:" +
                        "            [ 'another-node-label-value' ]}]}}]}");

        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        deployer = k8sAppDeployer(new KubernetesDeployerProperties());
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        NodeAffinity nodeAffinity = podSpec.getAffinity().getNodeAffinity();
        assertThat(nodeAffinity).as("Node affinity should not be null").isNotNull();
        assertThat(nodeAffinity.getRequiredDuringSchedulingIgnoredDuringExecution()).as("RequiredDuringSchedulingIgnoredDuringExecution should not be null").isNotNull();
        assertThat(nodeAffinity.getPreferredDuringSchedulingIgnoredDuringExecution().size()).as("PreferredDuringSchedulingIgnoredDuringExecution should have one element").isEqualTo(1);
    }

    @Test
    public void testPodAffinityProperty() {
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.affinity.podAffinity",
                "{ requiredDuringSchedulingIgnoredDuringExecution:" +
                        "  { labelSelector:" +
                        "    [ { matchExpressions:" +
                        "        [ { key: 'app', " +
                        "            operator: 'In'," +
                        "            values:" +
                        "            [ 'store']}]}], " +
                        "     topologyKey: 'kubernetes.io/hostname'}, " +
                        "  preferredDuringSchedulingIgnoredDuringExecution:" +
                        "  [ { weight: 1," +
                        "      podAffinityTerm:" +
                        "      { labelSelector:" +
                        "        { matchExpressions:" +
                        "          [ { key: 'security'," +
                        "              operator: 'In'," +
                        "              values:" +
                        "              [ 'S2' ]}]}, " +
                        "        topologyKey: 'failure-domain.beta.kubernetes.io/zone'}}]}");

        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        deployer = k8sAppDeployer(new KubernetesDeployerProperties());
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        PodAffinity podAffinity = podSpec.getAffinity().getPodAffinity();
        assertThat(podAffinity).as("Pod affinity should not be null").isNotNull();
        assertThat(podAffinity.getRequiredDuringSchedulingIgnoredDuringExecution()).as("RequiredDuringSchedulingIgnoredDuringExecution should not be null").isNotNull();
        assertThat(podAffinity.getPreferredDuringSchedulingIgnoredDuringExecution().size()).as("PreferredDuringSchedulingIgnoredDuringExecution should have one element").isEqualTo(1);
    }

    @Test
    public void testPodAntiAffinityProperty() {
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.affinity.podAntiAffinity",
                "{ requiredDuringSchedulingIgnoredDuringExecution:" +
                        "  { labelSelector:" +
                        "    [ { matchExpressions:" +
                        "        [ { key: 'app', " +
                        "            operator: 'In'," +
                        "            values:" +
                        "            [ 'store']}]}], " +
                        "     topologyKey: 'kubernetes.io/hostname'}, " +
                        "  preferredDuringSchedulingIgnoredDuringExecution:" +
                        "  [ { weight: 1," +
                        "      podAffinityTerm:" +
                        "      { labelSelector:" +
                        "        { matchExpressions:" +
                        "          [ { key: 'security'," +
                        "              operator: 'In'," +
                        "              values:" +
                        "              [ 'S2' ]}]}, " +
                        "        topologyKey: 'failure-domain.beta.kubernetes.io/zone'}}]}");

        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        deployer = k8sAppDeployer(new KubernetesDeployerProperties());
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        PodAntiAffinity podAntiAffinity = podSpec.getAffinity().getPodAntiAffinity();
        assertThat(podAntiAffinity).as("Pod anti-affinity should not be null").isNotNull();
        assertThat(podAntiAffinity.getRequiredDuringSchedulingIgnoredDuringExecution()).as("RequiredDuringSchedulingIgnoredDuringExecution should not be null").isNotNull();
        assertThat(podAntiAffinity.getPreferredDuringSchedulingIgnoredDuringExecution().size()).as("PreferredDuringSchedulingIgnoredDuringExecution should have one element").isEqualTo(1);
    }

    @Test
    public void testNodeAffinityGlobalProperty() {
        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), null);

        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();

        NodeSelectorTerm nodeSelectorTerm = new NodeSelectorTerm();
        nodeSelectorTerm.setMatchExpressions(Arrays.asList(new NodeSelectorRequirementBuilder()
                .withKey("kubernetes.io/e2e-az-name")
                .withOperator("In")
                .withValues("e2e-az1", "e2e-az2")
                .build()));
        NodeSelectorTerm nodeSelectorTerm2 = new NodeSelectorTerm();
        nodeSelectorTerm2.setMatchExpressions(Arrays.asList(new NodeSelectorRequirementBuilder()
                .withKey("another-node-label-key")
                .withOperator("In")
                .withValues("another-node-label-value2")
                .build()));
        PreferredSchedulingTerm preferredSchedulingTerm = new PreferredSchedulingTerm(nodeSelectorTerm2, 1);
        NodeAffinity nodeAffinity = new AffinityBuilder()
                .withNewNodeAffinity()
                .withNewRequiredDuringSchedulingIgnoredDuringExecution()
                .withNodeSelectorTerms(nodeSelectorTerm)
                .endRequiredDuringSchedulingIgnoredDuringExecution()
                .withPreferredDuringSchedulingIgnoredDuringExecution(preferredSchedulingTerm)
                .endNodeAffinity()
                .buildNodeAffinity();

        kubernetesDeployerProperties.setNodeAffinity(nodeAffinity);

        deployer = k8sAppDeployer(kubernetesDeployerProperties);
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        NodeAffinity nodeAffinityTest = podSpec.getAffinity().getNodeAffinity();
        assertThat(nodeAffinityTest).as("Node affinity should not be null").isNotNull();
        assertThat(nodeAffinityTest.getRequiredDuringSchedulingIgnoredDuringExecution()).as("RequiredDuringSchedulingIgnoredDuringExecution should not be null").isNotNull();
        assertThat(nodeAffinityTest.getPreferredDuringSchedulingIgnoredDuringExecution().size()).as("PreferredDuringSchedulingIgnoredDuringExecution should have one element").isEqualTo(1);
    }

    @Test
    public void testPodAffinityGlobalProperty() {
        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), null);

        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();

        LabelSelector labelSelector = new LabelSelector();
        labelSelector.setMatchExpressions(Arrays.asList(new LabelSelectorRequirementBuilder()
                .withKey("security")
                .withOperator("In")
                .withValues("S1")
                .build()));
        PodAffinityTerm podAffinityTerm = new PodAffinityTerm(labelSelector, null, null, null, null, "failure-domain.beta.kubernetes.io/zone");
        LabelSelector labelSelector2 = new LabelSelector();
        labelSelector2.setMatchExpressions(Arrays.asList(new LabelSelectorRequirementBuilder()
                .withKey("security")
                .withOperator("In")
                .withValues("s2")
                .build()));
        PodAffinityTerm podAffinityTerm2 = new PodAffinityTerm(labelSelector2, null, null, null, null, "failure-domain.beta.kubernetes.io/zone");
        WeightedPodAffinityTerm weightedPodAffinityTerm = new WeightedPodAffinityTerm(podAffinityTerm2, 100);
        PodAffinity podAffinity = new AffinityBuilder()
                .withNewPodAffinity()
                .withRequiredDuringSchedulingIgnoredDuringExecution(podAffinityTerm)
                .withPreferredDuringSchedulingIgnoredDuringExecution(weightedPodAffinityTerm)
                .endPodAffinity()
                .buildPodAffinity();

        kubernetesDeployerProperties.setPodAffinity(podAffinity);

        deployer = k8sAppDeployer(kubernetesDeployerProperties);
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        PodAffinity podAffinityTest = podSpec.getAffinity().getPodAffinity();
        assertThat(podAffinityTest).as("Pod affinity should not be null").isNotNull();
        assertThat(podAffinityTest.getRequiredDuringSchedulingIgnoredDuringExecution()).as("RequiredDuringSchedulingIgnoredDuringExecution should not be null").isNotNull();
        assertThat(podAffinityTest.getPreferredDuringSchedulingIgnoredDuringExecution().size()).as("PreferredDuringSchedulingIgnoredDuringExecution should have one element").isEqualTo(1);
    }

    @Test
    public void testPodAntiAffinityGlobalProperty() {
        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), null);

        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        kubernetesDeployerProperties.setStartupHttpProbePort(kubernetesDeployerProperties.getLivenessHttpProbePort());
        LabelSelector labelSelector = new LabelSelector();
        labelSelector.setMatchExpressions(Arrays.asList(new LabelSelectorRequirementBuilder()
                .withKey("app")
                .withOperator("In")
                .withValues("store")
                .build()));
        PodAffinityTerm podAffinityTerm = new PodAffinityTerm(labelSelector, null, null, null, null, "kubernetes.io/hostname");
        LabelSelector labelSelector2 = new LabelSelector();
        labelSelector2.setMatchExpressions(Arrays.asList(new LabelSelectorRequirementBuilder()
                .withKey("security")
                .withOperator("In")
                .withValues("s2")
                .build()));
        PodAffinityTerm podAffinityTerm2 = new PodAffinityTerm(labelSelector2, null, null, null, null, "failure-domain.beta.kubernetes.io/zone");
        WeightedPodAffinityTerm weightedPodAffinityTerm = new WeightedPodAffinityTerm(podAffinityTerm2, 100);
        PodAntiAffinity podAntiAffinity = new AffinityBuilder()
                .withNewPodAntiAffinity()
                .withRequiredDuringSchedulingIgnoredDuringExecution(podAffinityTerm)
                .withPreferredDuringSchedulingIgnoredDuringExecution(weightedPodAffinityTerm)
                .endPodAntiAffinity()
                .buildPodAntiAffinity();

        kubernetesDeployerProperties.setPodAntiAffinity(podAntiAffinity);

        deployer = k8sAppDeployer(kubernetesDeployerProperties);
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        PodAntiAffinity podAntiAffinityTest = podSpec.getAffinity().getPodAntiAffinity();
        assertThat(podAntiAffinityTest).as("Pod anti-affinity should not be null").isNotNull();
        assertThat(podAntiAffinityTest.getRequiredDuringSchedulingIgnoredDuringExecution()).as("RequiredDuringSchedulingIgnoredDuringExecution should not be null").isNotNull();
        assertThat(podAntiAffinityTest.getPreferredDuringSchedulingIgnoredDuringExecution().size()).as("PreferredDuringSchedulingIgnoredDuringExecution should have one element").isEqualTo(1);
    }

    @Test
    public void testNodeAffinityFromYaml() throws Exception {
        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), null);

        deployer = k8sAppDeployer();
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        NodeAffinity nodeAffinity = podSpec.getAffinity().getNodeAffinity();
        assertThat(nodeAffinity).as("Node affinity should not be null").isNotNull();
        assertThat(nodeAffinity.getRequiredDuringSchedulingIgnoredDuringExecution()).as("RequiredDuringSchedulingIgnoredDuringExecution should not be null").isNotNull();
        assertThat(nodeAffinity.getPreferredDuringSchedulingIgnoredDuringExecution().size()).as("PreferredDuringSchedulingIgnoredDuringExecution should have one element").isEqualTo(1);
    }

    @Test
    public void testPodAffinityFromYaml() throws Exception {
        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), null);

        deployer = k8sAppDeployer();
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        PodAffinity podAffinity = podSpec.getAffinity().getPodAffinity();
        assertThat(podAffinity).as("Pod affinity should not be null").isNotNull();
        assertThat(podAffinity.getRequiredDuringSchedulingIgnoredDuringExecution()).as("RequiredDuringSchedulingIgnoredDuringExecution should not be null").isNotNull();
        assertThat(podAffinity.getPreferredDuringSchedulingIgnoredDuringExecution().size()).as("PreferredDuringSchedulingIgnoredDuringExecution should have one element").isEqualTo(1);
    }

    @Test
    public void testPodAntiAffinityFromYaml() throws Exception {
        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), null);

        deployer = k8sAppDeployer();
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        PodAntiAffinity podAntiAffinity = podSpec.getAffinity().getPodAntiAffinity();
        assertThat(podAntiAffinity).as("Pod anti-affinity should not be null").isNotNull();
        assertThat(podAntiAffinity.getRequiredDuringSchedulingIgnoredDuringExecution()).as("RequiredDuringSchedulingIgnoredDuringExecution should not be null").isNotNull();
        assertThat(podAntiAffinity.getPreferredDuringSchedulingIgnoredDuringExecution().size()).as("PreferredDuringSchedulingIgnoredDuringExecution should have one element").isEqualTo(1);
    }

    @Test
    public void testNodeAffinityPropertyOverrideGlobal() {
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.affinity.nodeAffinity",
                "{ requiredDuringSchedulingIgnoredDuringExecution:" +
                        "  { nodeSelectorTerms:" +
                        "    [ { matchExpressions:" +
                        "        [ { key: 'kubernetes.io/e2e-az-name', " +
                        "            operator: 'In'," +
                        "            values:" +
                        "            [ 'e2e-az1', 'e2e-az2']}]}]}, " +
                        "  preferredDuringSchedulingIgnoredDuringExecution:" +
                        "  [ { weight: 1," +
                        "      preference:" +
                        "      { matchExpressions:" +
                        "        [ { key: 'another-node-label-key'," +
                        "            operator: 'In'," +
                        "            values:" +
                        "            [ 'another-node-label-value' ]}]}}]}");

        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();

        NodeSelectorTerm nodeSelectorTerm = new NodeSelectorTerm();
        nodeSelectorTerm.setMatchExpressions(Arrays.asList(new NodeSelectorRequirementBuilder()
                .withKey("kubernetes.io/e2e-az-name")
                .withOperator("In")
                .withValues("e2e-az1", "e2e-az2")
                .build()));
        NodeAffinity nodeAffinity = new AffinityBuilder()
                .withNewNodeAffinity()
                .withNewRequiredDuringSchedulingIgnoredDuringExecution()
                .withNodeSelectorTerms(nodeSelectorTerm)
                .endRequiredDuringSchedulingIgnoredDuringExecution()
                .endNodeAffinity()
                .buildNodeAffinity();

        kubernetesDeployerProperties.setNodeAffinity(nodeAffinity);

        deployer = k8sAppDeployer(kubernetesDeployerProperties);
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        NodeAffinity nodeAffinityTest = podSpec.getAffinity().getNodeAffinity();
        assertThat(nodeAffinityTest).as("Node affinity should not be null").isNotNull();
        assertThat(nodeAffinityTest.getRequiredDuringSchedulingIgnoredDuringExecution()).as("RequiredDuringSchedulingIgnoredDuringExecution should not be null").isNotNull();
        assertThat(nodeAffinityTest.getPreferredDuringSchedulingIgnoredDuringExecution().size()).as("PreferredDuringSchedulingIgnoredDuringExecution should have one element").isEqualTo(1);
    }

    @Test
    public void testPodAffinityPropertyOverrideGlobal() {
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.affinity.podAffinity",
                "{ requiredDuringSchedulingIgnoredDuringExecution:" +
                        "  { labelSelector:" +
                        "    [ { matchExpressions:" +
                        "        [ { key: 'security', " +
                        "            operator: 'In'," +
                        "            values:" +
                        "            [ 'S1']}]}], " +
                        "     topologyKey: 'failure-domain.beta.kubernetes.io/zone'}, " +
                        "  preferredDuringSchedulingIgnoredDuringExecution:" +
                        "  [ { weight: 1," +
                        "      podAffinityTerm:" +
                        "      { labelSelector:" +
                        "        { matchExpressions:" +
                        "          [ { key: 'security'," +
                        "              operator: 'In'," +
                        "              values:" +
                        "              [ 'S2' ]}]}, " +
                        "        topologyKey: 'failure-domain.beta.kubernetes.io/zone'}}]}");

        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();

        LabelSelector labelSelector = new LabelSelector();
        labelSelector.setMatchExpressions(Arrays.asList(new LabelSelectorRequirementBuilder()
                .withKey("tolerance")
                .withOperator("In")
                .withValues("Reliable")
                .build()));
        PodAffinityTerm podAffinityTerm = new PodAffinityTerm(labelSelector, null, null, null, null, "failure-domain.beta.kubernetes.io/zone");
        PodAffinity podAffinity = new AffinityBuilder()
                .withNewPodAffinity()
                .withRequiredDuringSchedulingIgnoredDuringExecution(podAffinityTerm)
                .endPodAffinity()
                .buildPodAffinity();

        kubernetesDeployerProperties.setPodAffinity(podAffinity);

        deployer = k8sAppDeployer(kubernetesDeployerProperties);
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        PodAffinity podAffinityTest = podSpec.getAffinity().getPodAffinity();
        assertThat(podAffinityTest).as("Pod affinity should not be null").isNotNull();
        assertThat(podAffinityTest.getRequiredDuringSchedulingIgnoredDuringExecution()).as("RequiredDuringSchedulingIgnoredDuringExecution should not be null").isNotNull();
        assertThat(podAffinityTest.getPreferredDuringSchedulingIgnoredDuringExecution().size()).as("PreferredDuringSchedulingIgnoredDuringExecution should have one element").isEqualTo(1);
    }

    @Test
    public void testPodAntiAffinityPropertyOverrideGlobal() {
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.affinity.podAntiAffinity",
                "{ requiredDuringSchedulingIgnoredDuringExecution:" +
                        "  { labelSelector:" +
                        "    [ { matchExpressions:" +
                        "        [ { key: 'app', " +
                        "            operator: 'In'," +
                        "            values:" +
                        "            [ 'store']}]}], " +
                        "     topologyKey: 'kubernetes.io/hostnam'}, " +
                        "  preferredDuringSchedulingIgnoredDuringExecution:" +
                        "  [ { weight: 1," +
                        "      podAffinityTerm:" +
                        "      { labelSelector:" +
                        "        { matchExpressions:" +
                        "          [ { key: 'security'," +
                        "              operator: 'In'," +
                        "              values:" +
                        "              [ 'S2' ]}]}, " +
                        "        topologyKey: 'failure-domain.beta.kubernetes.io/zone'}}]}");

        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();

        LabelSelector labelSelector = new LabelSelector();
        labelSelector.setMatchExpressions(Arrays.asList(new LabelSelectorRequirementBuilder()
                .withKey("version")
                .withOperator("Equals")
                .withValues("v1")
                .build()));
        PodAffinityTerm podAffinityTerm = new PodAffinityTerm(labelSelector, null, null, null, null, "kubernetes.io/hostnam");
        PodAntiAffinity podAntiAffinity = new AffinityBuilder()
                .withNewPodAntiAffinity()
                .withRequiredDuringSchedulingIgnoredDuringExecution(podAffinityTerm)
                .endPodAntiAffinity()
                .buildPodAntiAffinity();

        kubernetesDeployerProperties.setPodAntiAffinity(podAntiAffinity);

        deployer = k8sAppDeployer(kubernetesDeployerProperties);
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);

        PodAntiAffinity podAntiAffinityTest = podSpec.getAffinity().getPodAntiAffinity();
        assertThat(podAntiAffinityTest).as("Pod anti-affinity should not be null").isNotNull();
        assertThat(podAntiAffinityTest.getRequiredDuringSchedulingIgnoredDuringExecution()).as("RequiredDuringSchedulingIgnoredDuringExecution should not be null").isNotNull();
        assertThat(podAntiAffinityTest.getPreferredDuringSchedulingIgnoredDuringExecution().size()).as("PreferredDuringSchedulingIgnoredDuringExecution should have one element").isEqualTo(1);
    }

	@Nested
	@DisplayName("creates pod spec with pod security context")
    class CreatePodSpecWithPodSecurityContext {

        @Test
        @DisplayName("created from deployment property with all fields")
        void createdFromDeploymentPropertyWithAllFields() {
            KubernetesDeployerProperties globalDeployerProps = new KubernetesDeployerProperties();
            Map<String, String> deploymentProps = new HashMap<>();
			deploymentProps.put("spring.cloud.deployer.kubernetes.podSecurityContext", "{" +
					"  fsGroup: 65534" +
					", fsGroupChangePolicy: Always" +
					", runAsUser: 65534" +
					", runAsGroup: 65534" +
					", runAsNonRoot: true" +
					", seLinuxOptions: { level: \"s0:c123,c456\" }" +
					", seccompProfile: { type: Localhost, localhostProfile: my-profiles/profile-allow.json }" +
					", supplementalGroups: [65534, 65535]" +
					", sysctls: [{name: \"kernel.shm_rmid_forced\", value: 0}, {name: \"net.core.somaxconn\", value: 1024}]" +
					", windowsOptions: { gmsaCredentialSpec: \"specA\", gmsaCredentialSpecName: \"specA-name\", hostProcess: true, runAsUserName: \"userA\" }" +
					"}");
            PodSecurityContext expectedPodSecurityContext = new PodSecurityContextBuilder()
					.withFsGroup(65534L)
					.withFsGroupChangePolicy("Always")
                    .withRunAsUser(65534L)
					.withRunAsGroup(65534L)
					.withRunAsNonRoot(true)
					.withSeLinuxOptions(new SELinuxOptions("s0:c123,c456", null, null, null))
					.withSeccompProfile(new SeccompProfile("my-profiles/profile-allow.json", "Localhost"))
					.withSupplementalGroups(65534L, 65535L)
					.withSysctls(new Sysctl("kernel.shm_rmid_forced", "0"), new Sysctl("net.core.somaxconn", "1024"))
					.withWindowsOptions(new WindowsSecurityContextOptions("specA", "specA-name", true, "userA"))
                    .build();
            assertThatDeployerCreatesPodSpecWithPodSecurityContext(globalDeployerProps, deploymentProps, expectedPodSecurityContext);
        }

        @Test
        @DisplayName("created from deployment property with runAsUser only")
        void createdFromDeploymentPropertyWithRunAsUserOnly() {
            KubernetesDeployerProperties globalDeployerProps = new KubernetesDeployerProperties();
            Map<String, String> deploymentProps = new HashMap<>();
            deploymentProps.put("spring.cloud.deployer.kubernetes.podSecurityContext", "{runAsUser: 65534}");
            PodSecurityContext expectedPodSecurityContext = new PodSecurityContextBuilder()
                    .withRunAsUser(65534L)
                    .build();
            assertThatDeployerCreatesPodSpecWithPodSecurityContext(globalDeployerProps, deploymentProps, expectedPodSecurityContext);
        }

        @Test
        @DisplayName("created from deployment property with fsGroup only")
        void createdFromDeploymentPropertyWithFsGroupOnly() {
            KubernetesDeployerProperties globalDeployerProps = new KubernetesDeployerProperties();
            Map<String, String> deploymentProps = new HashMap<>();
            deploymentProps.put("spring.cloud.deployer.kubernetes.podSecurityContext", "{fsGroup: 65534}");
            PodSecurityContext expectedPodSecurityContext = new PodSecurityContextBuilder()
                    .withFsGroup(65534L)
                    .build();
            assertThatDeployerCreatesPodSpecWithPodSecurityContext(globalDeployerProps, deploymentProps, expectedPodSecurityContext);
        }

        @Test
        @DisplayName("created from deployment property with supplementalGroups only")
        void createdFromDeploymentPropertyWithSupplementalGroupsOnly() {
            KubernetesDeployerProperties globalDeployerProps = new KubernetesDeployerProperties();
            Map<String, String> deploymentProps = new HashMap<>();
            deploymentProps.put("spring.cloud.deployer.kubernetes.podSecurityContext", "{supplementalGroups: [65534,65535]}");
            PodSecurityContext expectedPodSecurityContext = new PodSecurityContextBuilder()
                    .withSupplementalGroups(65534L, 65535L)
                    .build();
            assertThatDeployerCreatesPodSpecWithPodSecurityContext(globalDeployerProps, deploymentProps, expectedPodSecurityContext);
        }

        @Test
        @DisplayName("created from deployment property with seccompProfile only")
        void createdFromDeploymentPropertyWithSeccompProfileOnly() {
            KubernetesDeployerProperties globalDeployerProps = new KubernetesDeployerProperties();
            Map<String, String> deploymentProps = new HashMap<>();
            deploymentProps.put("spring.cloud.deployer.kubernetes.podSecurityContext", "{seccompProfile: { type: RuntimeDefault}}");
            PodSecurityContext expectedPodSecurityContext = new PodSecurityContextBuilder()
                    .withSeccompProfile(new SeccompProfile(null, "RuntimeDefault"))
                    .build();
            assertThatDeployerCreatesPodSpecWithPodSecurityContext(globalDeployerProps, deploymentProps, expectedPodSecurityContext);
        }

        @Test
        @DisplayName("created from global deployer property sourced from yaml")
        void createdFromGlobalDeployerPropertySourcedFromYaml() throws Exception {
            KubernetesDeployerProperties globalDeployerProps = bindDeployerProperties();
            Map<String, String> deploymentProps = new HashMap<>();
			PodSecurityContext expectedPodSecurityContext = new PodSecurityContextBuilder()
					.withFsGroup(65534L)
					.withFsGroupChangePolicy("Always")
					.withRunAsUser(65534L)
					.withRunAsGroup(65534L)
					.withRunAsNonRoot(true)
					.withSeLinuxOptions(new SELinuxOptions("s0:c123,c456", null, null, null))
					.withSeccompProfile(new SeccompProfile("my-profiles/profile-allow.json", "Localhost"))
					.withSupplementalGroups(65534L, 65535L)
					.withSysctls(new Sysctl("kernel.shm_rmid_forced", "0"), new Sysctl("net.core.somaxconn", "1024"))
					.withWindowsOptions(new WindowsSecurityContextOptions("specA", "specA-name", true, "userA"))
					.build();
            assertThatDeployerCreatesPodSpecWithPodSecurityContext(globalDeployerProps, deploymentProps, expectedPodSecurityContext);
        }

        @Test
        @DisplayName("created from global deployer property")
        void createdFromGlobalDeployerProperty() {
            KubernetesDeployerProperties globalDeployerProps = new KubernetesDeployerProperties();
            KubernetesDeployerProperties.PodSecurityContext securityContext = new KubernetesDeployerProperties.PodSecurityContext();
            securityContext.setFsGroup(65534L);
            securityContext.setRunAsUser(65534L);
            securityContext.setSupplementalGroups(new Long[]{65534L});
            KubernetesDeployerProperties.SeccompProfile seccompProfile = new KubernetesDeployerProperties.SeccompProfile();
            seccompProfile.setType("Localhost");
            seccompProfile.setLocalhostProfile("profile.json");
            securityContext.setSeccompProfile(seccompProfile);
            globalDeployerProps.setPodSecurityContext(securityContext);
            Map<String, String> deploymentProps = Collections.emptyMap();
            PodSecurityContext expectedPodSecurityContext = new PodSecurityContextBuilder()
                    .withRunAsUser(65534L)
                    .withFsGroup(65534L)
                    .withSupplementalGroups(65534L)
                    .withSeccompProfile(new SeccompProfile("profile.json", "Localhost"))
                    .build();
            assertThatDeployerCreatesPodSpecWithPodSecurityContext(globalDeployerProps, deploymentProps, expectedPodSecurityContext);
        }

        @Test
        @DisplayName("created from deployment property overrriding global deployer property")
        void createdFromDeploymentPropertyOverridingGlobalDeployerProperty() {
            KubernetesDeployerProperties globalDeployerProps = new KubernetesDeployerProperties();
            KubernetesDeployerProperties.PodSecurityContext securityContext = new KubernetesDeployerProperties.PodSecurityContext();
            securityContext.setFsGroup(1000L);
            securityContext.setRunAsUser(1000L);
            securityContext.setSupplementalGroups(new Long[]{1000L});
            KubernetesDeployerProperties.SeccompProfile seccompProfile = new KubernetesDeployerProperties.SeccompProfile();
            seccompProfile.setType("Localhost");
            seccompProfile.setLocalhostProfile("sec/default-allow.json");
            securityContext.setSeccompProfile(seccompProfile);
            globalDeployerProps.setPodSecurityContext(securityContext);
            Map<String, String> deploymentProps = new HashMap<>();
            deploymentProps.put("spring.cloud.deployer.kubernetes.podSecurityContext", "{runAsUser: 65534, fsGroup: 65534, supplementalGroups: [65534,65535], seccompProfile: { type: Localhost, localhostProfile: sec/custom-allow.json } }");
            PodSecurityContext expectedPodSecurityContext = new PodSecurityContextBuilder()
                    .withRunAsUser(65534L)
                    .withFsGroup(65534L)
                    .withSupplementalGroups(65534L, 65535L)
                    .withSeccompProfile(new SeccompProfile("sec/custom-allow.json", "Localhost"))
                    .build();
            assertThatDeployerCreatesPodSpecWithPodSecurityContext(globalDeployerProps, deploymentProps, expectedPodSecurityContext);
        }

        private void assertThatDeployerCreatesPodSpecWithPodSecurityContext(
                KubernetesDeployerProperties globalDeployerProps,
                Map<String, String> deploymentProps,
                PodSecurityContext expectedPodSecurityContext
        ) {
            PodSpec podSpec = deployerCreatesPodSpec(globalDeployerProps, deploymentProps);
            PodSecurityContext actualPodSecurityContext = podSpec.getSecurityContext();
            assertThat(actualPodSecurityContext)
                    .isNotNull()
                    .isEqualTo(expectedPodSecurityContext);
        }
    }

    @Nested
    @DisplayName("creates pod spec with container security context")
    class CreatePodSpecWithContainerSecurityContext {

        @Test
        @DisplayName("created from deployment property with all fields")
        void createdFromDeploymentPropertyWithAllFields() {
            KubernetesDeployerProperties globalDeployerProps = new KubernetesDeployerProperties();
            Map<String, String> deploymentProps = new HashMap<>();
			deploymentProps.put("spring.cloud.deployer.kubernetes.containerSecurityContext", "{" +
					"  allowPrivilegeEscalation: true" +
					", capabilities: { add: [ \"a\", \"b\" ], drop: [ \"c\" ] }" +
					", privileged: true" +
					", procMount: DefaultProcMount" +
					", readOnlyRootFilesystem: true" +
					", runAsUser: 65534" +
					", runAsGroup: 65534" +
					", runAsNonRoot: true" +
					", seLinuxOptions: { level: \"s0:c123,c456\" }" +
					", seccompProfile: { type: Localhost, localhostProfile: my-profiles/profile-allow.json }" +
					", windowsOptions: { gmsaCredentialSpec: \"specA\", gmsaCredentialSpecName: \"specA-name\", hostProcess: true, runAsUserName: \"userA\" }" +
					"}");
			SecurityContext expectedContainerSecurityContext = new SecurityContextBuilder()
                    .withAllowPrivilegeEscalation(true)
					.withCapabilities(new Capabilities(Arrays.asList("a", "b"), Arrays.asList("c")))
					.withPrivileged(true)
					.withProcMount("DefaultProcMount")
					.withReadOnlyRootFilesystem(true)
					.withRunAsUser(65534L)
					.withRunAsGroup(65534L)
					.withRunAsNonRoot(true)
					.withSeLinuxOptions(new SELinuxOptions("s0:c123,c456", null, null, null))
					.withSeccompProfile(new SeccompProfile("my-profiles/profile-allow.json", "Localhost"))
					.withWindowsOptions(new WindowsSecurityContextOptions("specA", "specA-name", true, "userA"))
                    .build();
            assertThatDeployerCreatesPodSpecWithContainerSecurityContext(globalDeployerProps, deploymentProps, expectedContainerSecurityContext);
        }

        @Test
        @DisplayName("created from deployment property with allowPrivilegeEscalation only")
        void createdFromDeploymentPropertyWithAllowPrivilegeEscalationOnly() {
            KubernetesDeployerProperties globalDeployerProps = new KubernetesDeployerProperties();
            Map<String, String> deploymentProps = Collections.singletonMap("spring.cloud.deployer.kubernetes.containerSecurityContext", "{allowPrivilegeEscalation: true}");
            SecurityContext expectedContainerSecurityContext = new SecurityContextBuilder()
                    .withAllowPrivilegeEscalation(true)
                    .build();
            assertThatDeployerCreatesPodSpecWithContainerSecurityContext(globalDeployerProps, deploymentProps, expectedContainerSecurityContext);
        }

        @Test
        @DisplayName("created from deployment property with readOnlyRootFilesystem only")
        void createdFromDeploymentPropertyWithReadOnlyRootFilesystemOnly() {
            KubernetesDeployerProperties globalDeployerProps = new KubernetesDeployerProperties();
            Map<String, String> deploymentProps = Collections.singletonMap("spring.cloud.deployer.kubernetes.containerSecurityContext", "{readOnlyRootFilesystem: true}");
            SecurityContext expectedContainerSecurityContext = new SecurityContextBuilder()
                    .withReadOnlyRootFilesystem(true)
                    .build();
            assertThatDeployerCreatesPodSpecWithContainerSecurityContext(globalDeployerProps, deploymentProps, expectedContainerSecurityContext);
        }

        @Test
        @DisplayName("created from global deployer property sourced from yaml")
        void createdFromGlobalDeployerPropertySourcedFromYaml() throws Exception {
            KubernetesDeployerProperties globalDeployerProps = bindDeployerProperties();
            Map<String, String> deploymentProps = Collections.emptyMap();
			SecurityContext expectedContainerSecurityContext = new SecurityContextBuilder()
					.withAllowPrivilegeEscalation(true)
					.withCapabilities(new Capabilities(Arrays.asList("a", "b"), Arrays.asList("c")))
					.withPrivileged(true)
					.withProcMount("DefaultProcMount")
					.withReadOnlyRootFilesystem(true)
					.withRunAsUser(65534L)
					.withRunAsGroup(65534L)
					.withRunAsNonRoot(true)
					.withSeLinuxOptions(new SELinuxOptions("s0:c123,c456", null, null, null))
					.withSeccompProfile(new SeccompProfile("my-profiles/profile-allow.json", "Localhost"))
					.withWindowsOptions(new WindowsSecurityContextOptions("specA", "specA-name", true, "userA"))
					.build();
            assertThatDeployerCreatesPodSpecWithContainerSecurityContext(globalDeployerProps, deploymentProps, expectedContainerSecurityContext);
        }

        @Test
        @DisplayName("created from global deployer property")
        void createdFromGlobalDeployerProperty() {
            KubernetesDeployerProperties globalDeployerProps = new KubernetesDeployerProperties();
            KubernetesDeployerProperties.ContainerSecurityContext securityContext = new KubernetesDeployerProperties.ContainerSecurityContext();
            securityContext.setAllowPrivilegeEscalation(false);
            securityContext.setReadOnlyRootFilesystem(true);
            globalDeployerProps.setContainerSecurityContext(securityContext);
            Map<String, String> deploymentProps = Collections.emptyMap();
            SecurityContext expectedContainerSecurityContext = new SecurityContextBuilder()
                    .withAllowPrivilegeEscalation(false)
                    .withReadOnlyRootFilesystem(true)
                    .build();
            assertThatDeployerCreatesPodSpecWithContainerSecurityContext(globalDeployerProps, deploymentProps, expectedContainerSecurityContext);
        }

        @Test
        @DisplayName("created from deployment property overrriding global deployer property")
        void createdFromDeploymentPropertyOverridingGlobalDeployerProperty() {
            KubernetesDeployerProperties globalDeployerProps = new KubernetesDeployerProperties();
            KubernetesDeployerProperties.ContainerSecurityContext securityContext = new KubernetesDeployerProperties.ContainerSecurityContext();
            securityContext.setAllowPrivilegeEscalation(true);
            securityContext.setReadOnlyRootFilesystem(false);
            globalDeployerProps.setContainerSecurityContext(securityContext);
            Map<String, String> deploymentProps = Collections.singletonMap("spring.cloud.deployer.kubernetes.containerSecurityContext", "{allowPrivilegeEscalation: false, readOnlyRootFilesystem: true}");
            SecurityContext expectedContainerSecurityContext = new SecurityContextBuilder()
                    .withAllowPrivilegeEscalation(false)
                    .withReadOnlyRootFilesystem(true)
                    .build();
            assertThatDeployerCreatesPodSpecWithContainerSecurityContext(globalDeployerProps, deploymentProps, expectedContainerSecurityContext);
        }

        private void assertThatDeployerCreatesPodSpecWithContainerSecurityContext(
                KubernetesDeployerProperties globalDeployerProps,
                Map<String, String> deploymentProps,
                SecurityContext expectedContainerSecurityContext
        ) {
            PodSpec podSpec = deployerCreatesPodSpec(globalDeployerProps, deploymentProps);
            assertThat(podSpec.getContainers())
                    .singleElement()
                    .extracting(Container::getSecurityContext)
                    .isEqualTo(expectedContainerSecurityContext);
        }
    }

    private PodSpec deployerCreatesPodSpec(KubernetesDeployerProperties globalDeployerProperties, Map<String, String> deploymentProperties) {
        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), deploymentProperties);
        KubernetesAppDeployer deployer = k8sAppDeployer(globalDeployerProperties);
        return deployer.createPodSpec(appDeploymentRequest);
    }

    @Test
    public void testWithLifecyclePostStart() {
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.lifecycle.postStart.exec.command",
                "/bin/sh,-c,echo Hello from the postStart handler > /usr/share/message");
        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        deployer = k8sAppDeployer(kubernetesDeployerProperties);
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);
        assertThat(podSpec.getContainers().get(0).getLifecycle().getPostStart().getExec().getCommand())
                .containsExactlyInAnyOrder("/bin/sh", "-c", "echo Hello from the postStart handler > /usr/share/message");
    }

    @Test
    public void testWithLifecyclePreStop() {
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.lifecycle.preStop.exec.command",
                "/bin/sh,-c,nginx -s quit; while killall -0 nginx; do sleep 1; done");
        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        deployer = k8sAppDeployer(kubernetesDeployerProperties);
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);
        assertThat(podSpec.getContainers().get(0).getLifecycle().getPreStop().getExec().getCommand())
                .containsExactlyInAnyOrder(
                        "/bin/sh", "-c", "nginx -s quit; while killall -0 nginx; do sleep 1; done");
    }

    @Test
    public void testLifecyclePostStartOverridesGlobalPostStart() {
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.lifecycle.postStart.exec.command",
                "/bin/sh,-c,nginx -s quit; while killall -0 nginx; do sleep 1; done");
        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        KubernetesDeployerProperties.Lifecycle lifecycle = new KubernetesDeployerProperties.Lifecycle();
        lifecycle.setPostStart(new KubernetesDeployerProperties.Lifecycle.Hook() {
            @Override
            KubernetesDeployerProperties.Lifecycle.Exec getExec() {
                return new KubernetesDeployerProperties.Lifecycle.Exec() {
                    @Override
                    List<String> getCommand() {
                        return Arrays.asList("echo", "postStart");
                    }
                };
            }
        });
        lifecycle.setPreStop(new KubernetesDeployerProperties.Lifecycle.Hook() {
            @Override
            KubernetesDeployerProperties.Lifecycle.Exec getExec() {
                return new KubernetesDeployerProperties.Lifecycle.Exec() {
                    @Override
                    List<String> getCommand() {
                        return Arrays.asList("echo", "preStop");
                    }
                };
            }
        });
        kubernetesDeployerProperties.setLifecycle(lifecycle);
        deployer = k8sAppDeployer(kubernetesDeployerProperties);
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);
        assertThat(podSpec.getContainers().get(0).getLifecycle().getPostStart().getExec().getCommand())
                .containsExactlyInAnyOrder(
                        "/bin/sh", "-c", "nginx -s quit; while killall -0 nginx; do sleep 1; done");
        assertThat(podSpec.getContainers().get(0).getLifecycle().getPreStop().getExec().getCommand())
                .containsExactlyInAnyOrder("echo", "preStop");
    }

    @Test
    public void testLifecyclePrestopOverridesGlobalPrestop() {
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.lifecycle.preStop.exec.command",
                "/bin/sh,-c,nginx -s quit; while killall -0 nginx; do sleep 1; done");
        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        KubernetesDeployerProperties.Lifecycle lifecycle = new KubernetesDeployerProperties.Lifecycle();
        lifecycle.setPostStart(new KubernetesDeployerProperties.Lifecycle.Hook() {
            @Override
            KubernetesDeployerProperties.Lifecycle.Exec getExec() {
                return new KubernetesDeployerProperties.Lifecycle.Exec() {
                    @Override
                    List<String> getCommand() {
                        return Arrays.asList("echo", "postStart");
                    }
                };
            }
        });
        lifecycle.setPreStop(new KubernetesDeployerProperties.Lifecycle.Hook() {
            @Override
            KubernetesDeployerProperties.Lifecycle.Exec getExec() {
                return new KubernetesDeployerProperties.Lifecycle.Exec() {
                    @Override
                    List<String> getCommand() {
                        return Arrays.asList("echo", "preStop");
                    }
                };
            }
        });
        kubernetesDeployerProperties.setLifecycle(lifecycle);
        deployer = k8sAppDeployer(kubernetesDeployerProperties);
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);
        assertThat(podSpec.getContainers().get(0).getLifecycle().getPreStop().getExec().getCommand())
                .containsExactlyInAnyOrder(
                        "/bin/sh", "-c", "nginx -s quit; while killall -0 nginx; do sleep 1; done");
        assertThat(podSpec.getContainers().get(0).getLifecycle().getPostStart().getExec().getCommand())
                .containsExactlyInAnyOrder("echo", "postStart");
    }

    @Test
    public void terminationGracePeriodFromDeployerProp() {
        Map<String, String> props = new HashMap<>();
        props.put("spring.cloud.deployer.kubernetes.terminationGracePeriodSeconds", "5150");
        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        kubernetesDeployerProperties.setTerminationGracePeriodSeconds(6160L);
        deployer = k8sAppDeployer(kubernetesDeployerProperties);
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);
        assertThat(podSpec.getTerminationGracePeriodSeconds()).isEqualTo(5150L);
    }

    @Test
    public void terminationGracePeriodFromGlobalProp() {
        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), Collections.emptyMap());
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        kubernetesDeployerProperties.setTerminationGracePeriodSeconds(6160L);
        deployer = k8sAppDeployer(kubernetesDeployerProperties);
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);
        assertThat(podSpec.getTerminationGracePeriodSeconds()).isEqualTo(6160L);
    }

    @Test
    public void terminationGracePeriodNotSpecified() {
        AppDefinition definition = new AppDefinition("app-test", null);
        AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), Collections.emptyMap());
        KubernetesDeployerProperties kubernetesDeployerProperties = new KubernetesDeployerProperties();
        deployer = k8sAppDeployer(kubernetesDeployerProperties);
        PodSpec podSpec = deployer.createPodSpec(appDeploymentRequest);
        assertThat(podSpec.getTerminationGracePeriodSeconds()).isNull();
    }

    private Resource getResource() {
        return new DockerResource("springcloud/spring-cloud-deployer-spi-test-app:latest");
    }

    private KubernetesDeployerProperties bindDeployerProperties() throws Exception {
        YamlPropertiesFactoryBean properties = new YamlPropertiesFactoryBean();
        properties.setResources(new ClassPathResource("dataflow-server.yml"),
                new ClassPathResource("dataflow-server-tolerations.yml"),
                new ClassPathResource("dataflow-server-secretKeyRef.yml"),
                new ClassPathResource("dataflow-server-configMapKeyRef.yml"),
                new ClassPathResource("dataflow-server-podsecuritycontext.yml"),
                new ClassPathResource("dataflow-server-containerSecurityContext.yml"),
                new ClassPathResource("dataflow-server-nodeAffinity.yml"),
                new ClassPathResource("dataflow-server-podAffinity.yml"),
                new ClassPathResource("dataflow-server-podAntiAffinity.yml"));
        Properties yaml = properties.getObject();
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(yaml);
        return new Binder(source).bind("", Bindable.of(KubernetesDeployerProperties.class)).get();
    }

    protected KubernetesAppDeployer k8sAppDeployer() throws Exception {
        return k8sAppDeployer(bindDeployerProperties());
    }

    protected KubernetesAppDeployer k8sAppDeployer(KubernetesDeployerProperties kubernetesDeployerProperties) {
        return new KubernetesAppDeployer(kubernetesDeployerProperties, null);
    }

}
