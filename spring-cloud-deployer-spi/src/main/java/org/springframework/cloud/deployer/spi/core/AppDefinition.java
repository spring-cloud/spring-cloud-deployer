/*
 * Copyright 2016 the original author or authors.
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

package org.springframework.cloud.deployer.spi.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.core.style.ToStringCreator;
import org.springframework.util.Assert;

/**
 * Definition of an app, including its name and its properties.
 *
 * A deployer may not modify properties in this class as those are meant for an
 * actual app as is. The deployer's only responsibility is to pass those
 * properties into a runtime environment in a way that they are available in a
 * running application. For example, they would typically be passed as env vars.
 *
 * @author Mark Fisher
 * @author Janne Valkealahti
 */
public class AppDefinition {

	/**
	 * Name of the app.
	 */
	private final String name;

	/**
	 * Properties for this app.
	 */
	private final Map<String, String> properties;

	/**
	 * Construct an {@code AppDefinition}.
	 *
	 * @param name name of app
	 * @param properties app properties; may be {@code null}
	 */
	public AppDefinition(String name, Map<String, String> properties) {
		Assert.notNull(name, "name must not be null");
		this.name = name;
		this.properties = properties == null
				? Collections.<String, String>emptyMap()
				: Collections.unmodifiableMap(new HashMap<String, String>(properties));
	}

	/**
	 * Return the name of this app.
	 *
	 * @return the app name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Gets the app definition properties. These properties are passed into a running app.
	 *
	 * @return the unmodifiable map of app properties
	 */
	public Map<String, String> getProperties() {
		return properties;
	}

	@Override
	public String toString() {
		return new ToStringCreator(this)
				.append("name", this.name)
				.append("properties", this.properties).toString();
	}
}
