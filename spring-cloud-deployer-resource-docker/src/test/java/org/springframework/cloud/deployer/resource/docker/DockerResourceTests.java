/*
 * Copyright 2016 the original author or authors.
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

package org.springframework.cloud.deployer.resource.docker;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for the {@link DockerResource}.
 *
 * @author Thomas Risberg
 */
public class DockerResourceTests {

	String image = "sringcloud/hello-kube:latest";

	@Test
	public void testResource() throws IOException, URISyntaxException {
		DockerResource r = new DockerResource(image);
		assertEquals(image, r.getURI().getSchemeSpecificPart());
	}

	@Test
	public void testUri() throws IOException, URISyntaxException {
		DockerResource r = new DockerResource(URI.create(DockerResource.URI_SCHEME + ":" + image));
		assertEquals(image, r.getURI().getSchemeSpecificPart());
	}

	@Test
	public void testInvalidUri() {
		assertThrows(IllegalArgumentException.class, () -> {
			DockerResource r = new DockerResource(URI.create("http:" + image));
		});
	}
}
