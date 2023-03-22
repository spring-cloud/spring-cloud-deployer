/*
 * Copyright 2019-2021 the original author or authors.
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
package org.springframework.cloud.deployer.spi.kubernetes;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link EntryPointStyle}
 *
 * @author Chris Schaefer
 */
public class EntryPointStyleTests {

	@Test
	public void testInvalidEntryPointStyleDefaulting() {
		EntryPointStyle entryPointStyle = EntryPointStyle
				.relaxedValueOf("unknown");
		assertThat(entryPointStyle).isEqualTo(EntryPointStyle.exec);
	}

	@Test
	public void testMatchEntryPointStyle() {
		EntryPointStyle entryPointStyle = EntryPointStyle
				.relaxedValueOf("shell");
		assertThat(entryPointStyle).isEqualTo(EntryPointStyle.shell);
	}

	@Test
	public void testMixedCaseEntryPointStyle() {
		EntryPointStyle entryPointStyle = EntryPointStyle
				.relaxedValueOf("bOOt");
		assertThat(entryPointStyle).isEqualTo(EntryPointStyle.boot);
	}
}
