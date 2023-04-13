/*
 * Copyright 2020 the original author or authors.
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

import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.providers.http.HttpWagon;
import org.eclipse.aether.transport.wagon.WagonProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple implementation of a {@link WagonProvider} which creates and supports
 * those providers we need. Maven resolver itself only provides PlexusWagonProvider
 * which is more involved with actual maven pom configuration and would not
 * suit our needs as things get a bit crazy with it due to its use of Guice.
 *
 * @author Janne Valkealahti
 */
public class StaticWagonProvider implements WagonProvider {

	private static final Logger logger = LoggerFactory.getLogger(StaticWagonProvider.class);

	public StaticWagonProvider() {
	}

	public Wagon lookup( String roleHint ) throws Exception {
		logger.debug("Looking up wagon for roleHint {}", roleHint);
		if ("https".equals(roleHint)) {
			return new HttpWagon();
		} else if ("http".equals(roleHint)) {
			return new HttpWagon();
		}
		throw new IllegalArgumentException("No wagon available for " + roleHint);
	}

	public void release(Wagon wagon) {
		// nothing to do now
	}
}
