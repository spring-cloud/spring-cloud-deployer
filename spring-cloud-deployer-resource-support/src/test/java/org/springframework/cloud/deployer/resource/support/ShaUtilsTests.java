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

package org.springframework.cloud.deployer.resource.support;

import static org.junit.Assert.assertEquals;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

/**
 * Simple sha utils tests.
 *
 * @author Janne Valkealahti
 */
public class ShaUtilsTests {

	@Test
	public void testSimpleSmoke() {
		for (int j = 0; j < 100; j++) {
			Set<String> nodups = new HashSet<>();
			for (int i = 0; i < 1000; i++) {
				nodups.add(ShaUtils.sha1(randomString(20)));
			}
			assertEquals(nodups.size() == 1000, true);
		}
	}

	static final String CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz!£$%^&*()-=+_";
	static SecureRandom rnd = new SecureRandom();

	String randomString(int len) {
		StringBuilder sb = new StringBuilder(len);
		for (int i = 0; i < len; i++)
			sb.append(CHARS.charAt(rnd.nextInt(CHARS.length())));
		return sb.toString();
	}
}
