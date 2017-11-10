/*
 * Copyright 2017 the original author or authors.
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
package org.springframework.cloud.deployer.resource.support;

import java.net.MalformedURLException;

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

/**
 * Resource loader for HTTP/HTTPS schemes that doesn't cache resources.
 *
 * @author Ilayaperumal Gopinathan
 */
public class HttpResourceLoader extends DefaultResourceLoader {

	@Override
	public Resource getResource(String location) {
		try {
			return new HttpResource(location);
		}
		catch (MalformedURLException e) {
			throw new IllegalStateException(e);
		}
	}
}
