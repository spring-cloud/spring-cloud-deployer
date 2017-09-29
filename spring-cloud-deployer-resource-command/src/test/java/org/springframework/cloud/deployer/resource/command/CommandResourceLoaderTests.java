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

package org.springframework.cloud.deployer.resource.command;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.Test;

import org.springframework.core.io.Resource;

/**
 * Tests for the {@link CommandResourceLoader}.
 *
 * @author Ali Shahbour
 */
public class CommandResourceLoaderTests {

	@Test
	public void verifyCommandUri() throws IOException {
		String location = "command:/bin/pwd";
		CommandResourceLoader loader = new CommandResourceLoader();
		Resource resource = loader.getResource(location);
		assertEquals(CommandResource.class, resource.getClass());
		CommandResource commandResource = (CommandResource) resource;
		assertEquals(location, commandResource.getURI().toString());
		assertEquals("/bin/pwd", commandResource.getURI().getSchemeSpecificPart());
		assertEquals("command", commandResource.getURI().getScheme().toString());
	}

	@Test
	public void verifyImageUriWithoutPrefix() throws IOException {
		String location = "/bin/pwd";
		CommandResourceLoader loader = new CommandResourceLoader();
		Resource resource = loader.getResource(location);
		assertEquals(CommandResource.class, resource.getClass());
		CommandResource commandResource = (CommandResource) resource;
		assertEquals(CommandResource.URI_SCHEME + ":" + location, commandResource.getURI().toString());
		assertEquals("/bin/pwd", commandResource.getURI().getSchemeSpecificPart());
		assertEquals("command", commandResource.getURI().getScheme().toString());
	}

}
