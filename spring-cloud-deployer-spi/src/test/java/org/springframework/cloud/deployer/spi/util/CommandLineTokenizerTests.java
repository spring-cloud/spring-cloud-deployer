package org.springframework.cloud.deployer.spi.util;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link CommandLineTokenizer}.
 *
 * @author Eric Bottard
 */
public class CommandLineTokenizerTests {

	@Test
	public void testSimple() {
		CommandLineTokenizer tokenizer = new CommandLineTokenizer("  a   b cdef  ");
		Assert.assertEquals(Arrays.asList("a", "b", "cdef"), tokenizer.getArgs());
	}

	@Test
	public void testQuotes() {
		CommandLineTokenizer tokenizer = new CommandLineTokenizer("  'a   b' cdef gh \"i j\"");
		Assert.assertEquals(Arrays.asList("a   b", "cdef", "gh", "i j"), tokenizer.getArgs());
	}

	@Test
	public void testEscapes() {
		CommandLineTokenizer tokenizer = new CommandLineTokenizer("  'a \\' \\\" b' cdef gh \"i \\\"j\"");
		Assert.assertEquals(Arrays.asList("a ' \\\" b", "cdef", "gh", "i \"j"), tokenizer.getArgs());
	}

	@Test(expected = IllegalStateException.class)
	public void testUnbalancedQuotes() {
		CommandLineTokenizer tokenizer = new CommandLineTokenizer(" 'ab cd' 'ef gh");
	}


}
