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

package org.springframework.cloud.deployer.resource.docker;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * A {@link ResourceLoader} that loads {@link DockerResource}s from locations of the format
 * {@literal docker:<repository>:<tag>} where the value for "repository:tag" conforms to the rules
 * for referencing Docker images.
 *
 * @author Thomas Risberg
 */
public class DockerResourceLoader  implements ResourceLoader {

	private final ClassLoader classLoader = ClassUtils.getDefaultClassLoader();

	/**
	 * Returns a {@link DockerResource} for the provided location.
	 *
	 * @param location the image location. May optionally be preceded by {@value DockerResource#URI_SCHEME}
	 * followed by a colon, e.g. {@literal docker:springcloud/app-name:tag}
	 * @return the {@link DockerResource}
	 */
	@Override
	public Resource getResource(String location) {
		Assert.hasText(location, "image location is required");
		String image = location.replaceFirst(DockerResource.URI_SCHEME + ":", "");
		return new DockerResource(image);
	}

	/**
	 * Returns the {@link ClassLoader} for this ResourceLoader.
	 */
	@Override
	public ClassLoader getClassLoader() {
		return this.classLoader;
	}

}
