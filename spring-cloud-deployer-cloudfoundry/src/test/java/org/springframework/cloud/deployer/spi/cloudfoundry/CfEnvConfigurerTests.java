/*
 * Copyright 2020-2021 the original author or authors.
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

package org.springframework.cloud.deployer.spi.cloudfoundry;

import java.util.Collections;
import java.util.Map;

import org.cloudfoundry.util.FluentMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CfEnvConfigurerTests {

	@Test
	public void testCfEnvConfigurerActivateCloudProfile() {
		Map<String, String> env = CfEnvConfigurer.activateCloudProfile(
				Collections.emptyMap(), CfEnvConfigurer.SPRING_PROFILES_ACTIVE_FQN);
		assertThat(env).containsEntry(CfEnvConfigurer.SPRING_PROFILES_ACTIVE_FQN,
				CfEnvConfigurer.CLOUD_PROFILE_NAME);
		assertThat(env).doesNotContainKeys(
				CfEnvConfigurer.SPRING_PROFILES_ACTIVE,
				CfEnvConfigurer.SPRING_PROFILES_ACTIVE_HYPHENATED);
	}

	@Test
	public void testCfEnvConfigurerDoNotActivateCloudProfileIfNoneExists() {
		Map<String, String> env = CfEnvConfigurer.activateCloudProfile(
				Collections.emptyMap(), null);
		assertThat(env).doesNotContainKeys(CfEnvConfigurer.SPRING_PROFILES_ACTIVE_FQN,
				CfEnvConfigurer.SPRING_PROFILES_ACTIVE,
				CfEnvConfigurer.SPRING_PROFILES_ACTIVE_HYPHENATED);
	}

	@Test
	public void testCfEnvConfigurerActivateCloudProfileWithEmptyValue() {
		Map<String, String> env = CfEnvConfigurer.activateCloudProfile(
				FluentMap.<String, String>builder()
						.entry(CfEnvConfigurer.SPRING_PROFILES_ACTIVE, " ")
						.build(),
				CfEnvConfigurer.SPRING_PROFILES_ACTIVE);
		assertThat(env).containsEntry(CfEnvConfigurer.SPRING_PROFILES_ACTIVE,
				CfEnvConfigurer.CLOUD_PROFILE_NAME);
		assertThat(env).doesNotContainKeys(CfEnvConfigurer.SPRING_PROFILES_ACTIVE_FQN,
				CfEnvConfigurer.SPRING_PROFILES_ACTIVE_HYPHENATED);
	}

	@Test
	public void testCfEnvConfigurerAppendToActiveProfiles() {
		Map<String, String> env = CfEnvConfigurer.activateCloudProfile(
				FluentMap.<String, String>builder()
						.entry(CfEnvConfigurer.SPRING_PROFILES_ACTIVE_FQN, "foo,bar")
						.build(), null);
		assertThat(env).containsEntry(CfEnvConfigurer.SPRING_PROFILES_ACTIVE_FQN, "foo,bar,cloud");
		assertThat(env).doesNotContainKeys(CfEnvConfigurer.SPRING_PROFILES_ACTIVE,
				CfEnvConfigurer.SPRING_PROFILES_ACTIVE_HYPHENATED);
	}

	@Test
	public void testCfEnvConfigurerDoesNotAppendCloudProfileIfAlreadyThere() {
		Map<String, String> env = CfEnvConfigurer.activateCloudProfile(
				FluentMap.<String, String>builder()
						.entry(CfEnvConfigurer.SPRING_PROFILES_ACTIVE_FQN, "foo,cloud")
						.build(), null);
		assertThat(env).containsEntry(CfEnvConfigurer.SPRING_PROFILES_ACTIVE_FQN, "foo,cloud");

		env = CfEnvConfigurer.activateCloudProfile(
				FluentMap.<String, String>builder()
						.entry(CfEnvConfigurer.SPRING_PROFILES_ACTIVE_FQN, "cloud,foo,bar")
						.build(), null);
		assertThat(env).containsEntry(CfEnvConfigurer.SPRING_PROFILES_ACTIVE_FQN, "cloud,foo,bar");
	}

	@Test
	public void testCloudProfileAppendedToCommandLineArgs() {
		assertThat(CfEnvConfigurer.appendCloudProfileToSpringProfilesActiveArg("--spring-profiles-active=foo,bar"))
				.isEqualTo("--spring-profiles-active=foo,bar,cloud");
		assertThat(CfEnvConfigurer.appendCloudProfileToSpringProfilesActiveArg("--spring.profiles.active=foo"))
				.isEqualTo("--spring.profiles.active=foo,cloud");
		assertThat(CfEnvConfigurer.appendCloudProfileToSpringProfilesActiveArg("--spring.profiles.active=foo,cloud"))
				.isEqualTo("--spring.profiles.active=foo,cloud");
		assertThat(CfEnvConfigurer.appendCloudProfileToSpringProfilesActiveArg("--spring.profiles.active=cloud"))
				.isEqualTo("--spring.profiles.active=cloud");
		assertThat(CfEnvConfigurer.appendCloudProfileToSpringProfilesActiveArg("--baz=foo"))
				.isEqualTo("--baz=foo");
	}
}
