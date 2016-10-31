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

import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.springframework.cloud.deployer.spi.local.LocalDeployerProperties.LOCAL_PROPERTIES;

/**
 * A class to help create the execution command for launching a Java Process.
 *
 * @author Mark Pollack
 */
public class ExecutionCommandBuilder {

    private static final String MAIN =  "main";

    private static final String CLASSPATH = "classpath";

    public void addJavaOptions(List<String> commands, Map<String, String> deploymentProperties,
                               LocalDeployerProperties localDeployerProperties) {
        String memory = null;
        if (containsKey(deploymentProperties, "memory")) {
            memory = "-Xmx" + getValue(deploymentProperties, "memory");
        }

        String javaOptsString = getValue(deploymentProperties, "javaOpts");
        if (javaOptsString == null && memory != null) {
            commands.add(memory);
        }

        if (javaOptsString != null) {
            String[] javaOpts = StringUtils.tokenizeToStringArray(javaOptsString, " ");
            boolean noJavaMemoryOption = !Stream.of(javaOpts).anyMatch(s -> s.startsWith("-Xmx"));
            if (noJavaMemoryOption && memory != null) {
                commands.add(memory);
            }
            commands.addAll(Arrays.asList(javaOpts));
        } else {
            if (localDeployerProperties.getJavaOpts() != null) {
                String[] javaOpts = StringUtils.tokenizeToStringArray(localDeployerProperties.getJavaOpts(), " ");
                commands.addAll(Arrays.asList(javaOpts));
            }
        }
    }

    public void addJavaExecutionOptions(List<String> commands, AppDeploymentRequest request) {
        Map<String, String> deploymentProperties = request.getDeploymentProperties();
        if (containsKey(deploymentProperties, MAIN) || containsKey(deploymentProperties, CLASSPATH)) {
            Assert.isTrue(containsKey(deploymentProperties, MAIN)
                            && containsKey(deploymentProperties, CLASSPATH),
                    LOCAL_PROPERTIES + "." + MAIN + " and " + LOCAL_PROPERTIES + "." + CLASSPATH +
                            " deployment properties are both required if either is provided.");
            commands.add("-cp");
            commands.add(getValue(deploymentProperties, CLASSPATH));
            commands.add(getValue(deploymentProperties, MAIN));
        }
        else {
            commands.add("-jar");
            Resource resource = request.getResource();
            try {
                commands.add(resource.getFile().getAbsolutePath());
            }
            catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private boolean containsKey(Map<String, String> deploymentProperties, String propertyName) {
        return deploymentProperties.containsKey(LOCAL_PROPERTIES + "." + propertyName);
    }

    private String getValue(Map<String, String> deploymentProperties, String propertyName) {
        return deploymentProperties.get(LOCAL_PROPERTIES + "." + propertyName);
    }
}
