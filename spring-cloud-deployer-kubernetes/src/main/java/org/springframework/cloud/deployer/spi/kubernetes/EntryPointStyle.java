/*
 * Copyright 2018-2019 the original author or authors.
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

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/**
 * Defines container entry point styles that are available. The selected entry point style
 * will determine how application properties are made available to the container.
 *
 * @author Chris Schaefer
 */
public enum EntryPointStyle {
	/**
	 * Application properties will be provided to the container as command line arguments.
	 */
	exec,

	/**
	 * Application properties will be provided to the container as environment variables.
	 */
	shell,

	/**
	 * Application properties will be provided to the container as JSON in the
	 * SPRING_APPLICATION_JSON environment variable. Command line arguments will be passed
	 * as-is.
	 */
	boot;

	/**
	 * Converts the string of the provided entry point style to the appropriate enum value.
	 * Defaults to {@link EntryPointStyle#exec} if no matching
	 * entry point style is found.
	 *
	 * @param entryPointStyle the entry point style to use
	 * @return the converted {@link EntryPointStyle}
	 */
	public static EntryPointStyle relaxedValueOf(String entryPointStyle) {
		// 'value' is just a dummy key as you can't bind a single value to an enum
		Map<String, String> props = new HashMap<>();
		props.put("value", entryPointStyle);
		MapConfigurationPropertySource source = new MapConfigurationPropertySource(props);
		Binder binder = new Binder(source);
		try {
			return binder.bind("value", Bindable.of(EntryPointStyle.class)).get();
		} catch (Exception e) {
			// error means we couldn't bind, caller seem to handle null
		}
		return exec;
	}
}
