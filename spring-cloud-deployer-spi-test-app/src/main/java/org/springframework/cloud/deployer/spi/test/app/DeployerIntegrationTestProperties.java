/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.deployer.spi.test.app;

import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the IntegrationTestProcessor app.
 *
 * @author Eric Bottard
 */
@ConfigurationProperties
public class DeployerIntegrationTestProperties {

	public static final String FUNNY_CHARACTERS = "&'\"|< é\\(";

	/**
	 * The delay in milliseconds to stall the initialization of this app.
	 * Useful for testing the 'deploying' state of a app.
	 */
	private int initDelay = 0;

	/**
	 * The delay in milliseconds after which this app will kill itself.
	 * <p>-1 means don't kill</p>
	 */
	private int killDelay = -1;

	/**
	 * If not empty, only the app intances whose number(s) are contained in this set
	 * will behave according to the other configuration parameters.
	 */
	private Set<Integer> matchInstances = new HashSet<>();

	/**
	 * If not null, this property will be tested against {@link #FUNNY_CHARACTERS}.
	 * This makes sure that a deployer knows how to properly propagate deployment properties, including
	 * those that contain chars that often require some form of escaping.
	 */
	private String parameterThatMayNeedEscaping;

	@Value("${INSTANCE_INDEX:${CF_INSTANCE_INDEX:0}}")
	private int instanceIndex = 0;

	public int getInitDelay() {
		return initDelay;
	}

	public void setInitDelay(int initDelay) {
		this.initDelay = initDelay;
	}

	public int getKillDelay() {
		return killDelay;
	}

	public void setKillDelay(int killDelay) {
		this.killDelay = killDelay;
	}

	public Set<Integer> getMatchInstances() {
		return matchInstances;
	}

	public void setMatchInstances(Set<Integer> matchInstances) {
		this.matchInstances = matchInstances;
	}

	public String getParameterThatMayNeedEscaping() {
		return parameterThatMayNeedEscaping;
	}

	public void setParameterThatMayNeedEscaping(String parameterThatMayNeedEscaping) {
		this.parameterThatMayNeedEscaping = parameterThatMayNeedEscaping;
	}

	public int getInstanceIndex() {
		return instanceIndex;
	}

	public void setInstanceIndex(int instanceIndex) {
		this.instanceIndex = instanceIndex;
	}
}
