/*
 * Copyright 2018 the original author or authors.
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

package org.springframework.cloud.deployer.resource.maven;

import java.util.Set;
import java.util.TreeSet;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AspectJ advice that logs the first time the {@link MavenArtifactResolver} <pre>resolve()</pre> method is invoked
 * by a {@link MavenResource} by instances with the same fileName. This avoids duplicate log messages since
 * <pre>MavenResource</pre> is not a singleton and each instance invokes <pre></pre>MavenArtifactResolver.resolve()</pre>
 * multiple times in its lifecycle.
 *
 * @author David Turanski
 **/
aspect MavenArtifactResolverAspect {

	private final Set<String> resolvedResources = new TreeSet();

	private static Logger logger = LoggerFactory.getLogger(MavenArtifactResolver.class);

	@Before(
		"execution( * org.springframework.cloud.deployer.resource.maven.MavenArtifactResolver.resolve(..))")
	public void trackResolvedResource(JoinPoint joinPoint) {
		MavenResource resource = (MavenResource) joinPoint.getArgs()[0];
		if (!resolvedResources.contains(resource.getFilename())) {
			logger.info("Resolving Maven resource {}. This may take some time if the artifact must be downloaded "
				+ "from a remote Maven repository.", resource.getFilename());
			resolvedResources.add(resource.getFilename());
		}
	}
}

