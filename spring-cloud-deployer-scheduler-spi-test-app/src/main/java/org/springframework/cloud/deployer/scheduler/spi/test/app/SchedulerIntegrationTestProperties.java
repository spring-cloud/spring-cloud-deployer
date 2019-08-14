/*
 * Copyright 2018 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *          https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.springframework.cloud.deployer.scheduler.spi.test.app;

import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the IntegrationTestProcessor app.
 *
 * @author Glenn Renfro
 */
@ConfigurationProperties
public class SchedulerIntegrationTestProperties {

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
	 * The exit code used when this app will kill itself.
	 * <p>Set this to 0 for normal exit</p>
	 */
	private int exitCode = 1;

	/**
	 * If not empty, only the app intances whose number(s) are contained in this set
	 * will behave according to the other configuration parameters.
	 */
	private Set<Integer> matchInstances = new HashSet<>();

	/**
	 * If not null, this property will be tested against {@link #FUNNY_CHARACTERS}.
	 * This makes sure that a deployer knows how to properly propagate application properties, including
	 * those that contain chars that often require some form of escaping.
	 */
	private String parameterThatMayNeedEscaping;

	/**
	 * If not null, this property will be tested against {@link #FUNNY_CHARACTERS}.
	 * This makes sure that a deployer knows how to properly propagate deployment properties, including
	 * those that contain chars that often require some form of escaping.
	 */
	private String commandLineArgValueThatMayNeedEscaping;

	@Value("${INSTANCE_INDEX:${CF_INSTANCE_INDEX:0}}")
	private Integer instanceIndex;

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

	public int getExitCode() {
		return exitCode;
	}

	public void setExitCode(int exitCode) {
		this.exitCode = exitCode;
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

	public Integer getInstanceIndex() {
		return instanceIndex;
	}

	public void setInstanceIndex(Integer instanceIndex) {
		this.instanceIndex = instanceIndex;
	}

	public String getCommandLineArgValueThatMayNeedEscaping() {
		return commandLineArgValueThatMayNeedEscaping;
	}

	public void setCommandLineArgValueThatMayNeedEscaping(String commandLineArgValueThatMayNeedEscaping) {
		this.commandLineArgValueThatMayNeedEscaping = commandLineArgValueThatMayNeedEscaping;
	}
}
