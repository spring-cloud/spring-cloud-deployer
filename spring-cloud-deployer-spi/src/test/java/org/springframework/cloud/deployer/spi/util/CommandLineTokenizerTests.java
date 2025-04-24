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

import java.util.Arrays;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link CommandLineTokenizer}.
 *
 * @author Eric Bottard
 */
public class CommandLineTokenizerTests {

	@Test
	public void testSimple() {
		CommandLineTokenizer tokenizer = new CommandLineTokenizer("a b cdef");
		Assertions.assertEquals(Arrays.asList("a", "b", "cdef"), tokenizer.getArgs());
		tokenizer = new CommandLineTokenizer("  a   b cdef  ");
		Assertions.assertEquals(Arrays.asList("a", "b", "cdef"), tokenizer.getArgs());
	}

	@Test
	public void testQuotes() {
		CommandLineTokenizer tokenizer = new CommandLineTokenizer("  'a   b' cdef gh \"i j\"");
		Assertions.assertEquals(Arrays.asList("a   b", "cdef", "gh", "i j"), tokenizer.getArgs());
	}

	@Test
	public void testEscapes() {
		CommandLineTokenizer tokenizer = new CommandLineTokenizer("  'a \\' \\\" b' cdef gh \"i \\\"j\"");
		Assertions.assertEquals(Arrays.asList("a ' \\\" b", "cdef", "gh", "i \"j"), tokenizer.getArgs());
	}

	@Test
	public void testUnbalancedQuotes() {
		assertThrows(IllegalStateException.class, () -> {
			CommandLineTokenizer tokenizer = new CommandLineTokenizer(" 'ab cd' 'ef gh");
		});
	}


}
