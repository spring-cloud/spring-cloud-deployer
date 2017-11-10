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

import java.io.File;

import org.junit.Test;

import org.springframework.util.Assert;

/**
 * @author Ilayaperumal Gopinathan
 */
public class HttpResourceTests {

	@Test
	public void testHttpResourceGetFile() throws Exception {
		String location = "https://github.com/spring-cloud/spring-cloud-deployer/blob/master/pom.xml";
		HttpResource httpResource = new HttpResource(location);
		File file1 = httpResource.getFile();
		File file2 = httpResource.getFile();
		Assert.isTrue(!file1.getPath().equals(file2.getPath()), "Files should be different");
	}
}
