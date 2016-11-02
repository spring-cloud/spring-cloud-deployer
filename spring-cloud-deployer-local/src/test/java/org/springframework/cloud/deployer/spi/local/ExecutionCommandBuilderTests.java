/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.deployer.spi.local;

import org.junit.Before;
import org.junit.Test;

import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.cloud.deployer.spi.local.LocalDeployerProperties.PREFIX;

public class ExecutionCommandBuilderTests {

    private ExecutionCommandBuilder commandBuilder;
    private List<String> args;
    private Map<String, String> deploymentProperties;
    private LocalDeployerProperties localDeployerProperties;

    @Before
    public void setUp() {
        commandBuilder = new ExecutionCommandBuilder();
        args = new ArrayList<>();
        deploymentProperties = new HashMap<>();
        localDeployerProperties = new LocalDeployerProperties();

    }

    @Test
    public void testDirectJavaMemoryOption() {
        deploymentProperties.put(AppDeployer.MEMORY_PROPERTY_KEY, "1024m");
        commandBuilder.addJavaOptions(args, deploymentProperties, localDeployerProperties);
        assertThat(args.size(), is(1));
        assertThat(args.get(0), is("-Xmx1024m"));
    }

    @Test
    public void testJavaMemoryOption() {
        deploymentProperties.put(PREFIX + ".javaOpts", "-Xmx1024m");
        commandBuilder.addJavaOptions(args, deploymentProperties, localDeployerProperties);
        assertThat(args.size(), is(1));
        assertThat(args.get(0), is("-Xmx1024m"));
    }

    @Test
    public void testOverrideMemoryOptions() {
        deploymentProperties.put(AppDeployer.MEMORY_PROPERTY_KEY, "1024m");
        deploymentProperties.put(PREFIX + ".javaOpts", "-Xmx2048m");
        commandBuilder.addJavaOptions(args, deploymentProperties, localDeployerProperties);
        assertThat(args.size(), is(1));
        assertThat(args.get(0), is("-Xmx2048m"));
    }

    @Test
    public void testDirectMemoryOptionsWithOtherOptions() {
        deploymentProperties.put(AppDeployer.MEMORY_PROPERTY_KEY, "1024m");
        deploymentProperties.put(PREFIX + ".javaOpts", "-Dtest=foo");
        commandBuilder.addJavaOptions(args, deploymentProperties, localDeployerProperties);
        assertThat(args.size(), is(2));
        assertThat(args.get(0), is("-Xmx1024m"));
        assertThat(args.get(1), is("-Dtest=foo"));
    }

    @Test
    public void testMultipleOptions() {
        deploymentProperties.put(PREFIX + ".javaOpts", "-Dtest=foo -Dbar=baz");
        commandBuilder.addJavaOptions(args, deploymentProperties, localDeployerProperties);
        assertThat(args.size(), is(2));
        assertThat(args.get(0), is("-Dtest=foo"));
        assertThat(args.get(1), is("-Dbar=baz"));
    }

    @Test
    public void testConfigurationPropertiesOverride() {
        localDeployerProperties.setJavaOpts("-Dfoo=test -Dbaz=bar");
        commandBuilder.addJavaOptions(args, deploymentProperties, localDeployerProperties);
        assertThat(args.size(), is(2));
        assertThat(args.get(0), is("-Dfoo=test"));
        assertThat(args.get(1), is("-Dbaz=bar"));
    }


    @Test
    public void testJarExecution() throws MalformedURLException {
        AppDefinition definition = new AppDefinition("randomApp", new HashMap<>());
        deploymentProperties.put(PREFIX + ".javaOpts", "-Dtest=foo -Dbar=baz");
        AppDeploymentRequest appDeploymentRequest =
                new AppDeploymentRequest(definition, testResource(), deploymentProperties);
        commandBuilder.addJavaExecutionOptions(args, appDeploymentRequest);
        assertThat(args.size(), is(2));
        assertThat(args.get(0), is("-jar"));
        assertThat(args.get(1), containsString("testResource.txt"));
    }

    @Test(expected = IllegalStateException.class)
    public void testBadResourceExecution() throws MalformedURLException {
        AppDefinition definition = new AppDefinition("randomApp", new HashMap<>());
        deploymentProperties.put(PREFIX + ".javaOpts", "-Dtest=foo -Dbar=baz");
        AppDeploymentRequest appDeploymentRequest =
                new AppDeploymentRequest(definition, new UrlResource("http://spring.io"), deploymentProperties);
        commandBuilder.addJavaExecutionOptions(args, appDeploymentRequest);
    }

    @Test
    public void testMainExecution() throws MalformedURLException {
        String mainApp = "org.foo.Main";
        String mainJar = "/tmp/myapp.jar";
        AppDefinition definition = new AppDefinition("randomApp", new HashMap<>());
        deploymentProperties.put(PREFIX + ".main", mainApp);
        deploymentProperties.put(PREFIX + ".classpath", mainJar);
        AppDeploymentRequest appDeploymentRequest =
                new AppDeploymentRequest(definition, testResource(), deploymentProperties);
        commandBuilder.addJavaExecutionOptions(args, appDeploymentRequest);
        assertThat(args.size(), is(3));
        assertThat(args.get(0), is("-cp"));
        assertThat(args.get(1), is(mainJar));
        assertThat(args.get(2), is(mainApp));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingMain() throws MalformedURLException {
        String mainJar = "/tmp/myapp.jar";
        AppDefinition definition = new AppDefinition("randomApp", new HashMap<>());
        deploymentProperties.put(PREFIX + ".classpath", mainJar);
        AppDeploymentRequest appDeploymentRequest =
                new AppDeploymentRequest(definition, testResource(), deploymentProperties);
        commandBuilder.addJavaExecutionOptions(args, appDeploymentRequest);
    }



    protected Resource testResource() throws MalformedURLException {
        return new ClassPathResource("testResource.txt");
    }

}
