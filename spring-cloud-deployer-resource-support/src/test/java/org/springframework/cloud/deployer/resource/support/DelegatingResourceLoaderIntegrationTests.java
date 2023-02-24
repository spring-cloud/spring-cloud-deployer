/*
 * Copyright 2017 the original author or authors.
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
package org.springframework.cloud.deployer.resource.support;

import java.io.File;

import org.junit.Test;

import org.springframework.core.io.Resource;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Mark Pollack
 */
public class DelegatingResourceLoaderIntegrationTests {

	@Test
	public void test() throws Exception {
		DelegatingResourceLoader delegatingResourceLoader = new DelegatingResourceLoader();
		Resource resource = delegatingResourceLoader.getResource("https://repo.maven.apache.org/maven2/org/springframework/cloud/stream/app/file-sink-rabbit/1.2.0.RELEASE/file-sink-rabbit-1.2.0.RELEASE.jar");
		File file1 = resource.getFile();
		File file2 = resource.getFile();
		assertThat(file1, is(equalTo(file2)));
	}
}
