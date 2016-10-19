package org.springframework.cloud.deployer.spi.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A general purpose tokenizer for "command line" arrays. Allows tokenizing a single String into an array of args,
 * splitting the array on space characters and trimming. Quoting using single and double quotes is supported, in which
 * case those quotes can be escaped using a backslash character.
 *
 * @author Eric Bottard
 */
public class CommandLineTokenizer {


	private final char[] buffer;

	private int pos;

	private static final char ESCAPE_CHAR = '\\';

	private final List<String> args = new ArrayList<>();

	public CommandLineTokenizer(String value) {
		this.buffer = value.toCharArray();
		tokenize();
	}

	public List<String> getArgs() {
		return Collections.unmodifiableList(args);
	}

	private void tokenize() {
		while (pos < buffer.length) {
			eatWhiteSpace();
			if (pos < buffer.length) {
				eatArg();
			}
		}
	}

	private void eatWhiteSpace() {
		while (pos < buffer.length && buffer[pos] == ' ') {
			pos++;
		}
	}

	private void eatArg() {
		char endDelimiter;
		if (buffer[pos] == '\'' || buffer[pos] == '"') {
			endDelimiter = buffer[pos++];
		}
		else {
			endDelimiter = ' ';
		}

		StringBuilder sb = new StringBuilder();
		while (pos < buffer.length && buffer[pos] != endDelimiter) {
			if (buffer[pos] == ESCAPE_CHAR) {
				sb.append(processCharacterEscapeCodes(endDelimiter));
			}
			else {
				sb.append(buffer[pos++]);
			}
		}
		if (pos == buffer.length && endDelimiter != ' ') {
			throw new IllegalStateException(String.format("Ran out of input in [%s], expected closing [%s]", new String(buffer), endDelimiter));
		}
		else if (endDelimiter != ' ' && buffer[pos] == endDelimiter) {
			pos++;
		}
		args.add(sb.toString());
	}

	/**
	 * When the escape character is encountered, consume and return the escaped sequence. Note that depending on which
	 * end delimiter is currently in use, not all combinations need to be escaped
	 * @param endDelimiter the current endDelimiter
	 */
	private char processCharacterEscapeCodes(char endDelimiter) {
		pos++;
		if (pos >= buffer.length) {
			throw new IllegalStateException("Ran out of input in escape sequence");
		}
		if (buffer[pos] == ESCAPE_CHAR) {
			pos++; // consume the second escape char
			return ESCAPE_CHAR;
		}
		else if (buffer[pos] == endDelimiter) {
			pos++;
			return endDelimiter;
		}
		else {
			// Not an actual escape. Do not increment pos,
			// and return the \ we consumed at the very beginning
			return ESCAPE_CHAR;
		}
	}

}
