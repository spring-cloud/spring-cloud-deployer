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
package org.springframework.cloud.deployer.spi.util;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link ByteSizeUtils}.
 *
 * @author Eric Bottard
 */
public class ByteSizeUtilsTests {

	@Test
	public void testParse() {
		assertThat(ByteSizeUtils.parseToMebibytes("1"), CoreMatchers.is(1L));
		assertThat(ByteSizeUtils.parseToMebibytes("2m"), CoreMatchers.is(2L));
		assertThat(ByteSizeUtils.parseToMebibytes("20M"), CoreMatchers.is(20L));
		assertThat(ByteSizeUtils.parseToMebibytes("1000g"), CoreMatchers.is(1024_000L));
		assertThat(ByteSizeUtils.parseToMebibytes("1G"), CoreMatchers.is(1024L));
	}

	@Test
	public void testNotANumber() {
		assertThrows(IllegalArgumentException.class, () ->
			ByteSizeUtils.parseToMebibytes("wat?124"));
	}

	@Test
	public void testUnsupportedUnit() {
		assertThrows(IllegalArgumentException.class, () ->
			ByteSizeUtils.parseToMebibytes("1PB"));
	}

}
