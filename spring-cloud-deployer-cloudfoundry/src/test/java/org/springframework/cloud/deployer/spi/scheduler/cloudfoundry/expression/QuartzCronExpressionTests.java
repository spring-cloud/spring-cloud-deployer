/*
 * Copyright 2018-2021 the original author or authors.
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

package org.springframework.cloud.deployer.spi.scheduler.cloudfoundry.expression;

import java.text.ParseException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class QuartzCronExpressionTests {

	/*
	 * Verifies that storeExpressionVals correctly calculates the month number
	 */
	@Test
	public void testStoreExpressionVal() {
		assertExpression("* * * * Foo ? ", "Invalid Month value:",
				"Expected ParseException did not fire for non-existent month");
		assertExpression("* * * * Jan-Foo ? ", "Invalid Month value:",
				"Expected ParseException did not fire for non-existent month");
	}

	@Test
	public void testWildCard() {
		assertExpression("0 0 * * * *",
				"Support for specifying both a day-of-week AND a day-of-month parameter is not implemented.",
				"Expected ParseException did not fire for wildcard day-of-month and day-of-week");
		assertExpression("0 0 * 4 * *",
				"Support for specifying both a day-of-week AND a day-of-month parameter is not implemented.",
				"Expected ParseException did not fire for specified day-of-month and wildcard day-of-week");
		assertExpression("0 0 * * * 4",
				"Support for specifying both a day-of-week AND a day-of-month parameter is not implemented.",
				"Expected ParseException did not fire for wildcard day-of-month and specified day-of-week");
	}

	@Test
	public void testForInvalidLInCronExpression() {
		assertExpression("0 43 9 1,5,29,L * ?",
				"Support for specifying 'L' and 'LW' with other days of the month is not implemented",
				"Expected ParseException did not fire for L combined with other days of the month");
		assertExpression("0 43 9 ? * SAT,SUN,L",
				"Support for specifying 'L' with other days of the week is not implemented",
				"Expected ParseException did not fire for L combined with other days of the week");
		assertExpression("0 43 9 ? * 6,7,L",
				"Support for specifying 'L' with other days of the week is not implemented",
				"Expected ParseException did not fire for L combined with other days of the week");
		assertThatCode(() -> {
			new QuartzCronExpression("0 43 9 ? * 5L");
		}).as("Unexpected ParseException thrown for supported '5L' expression.").doesNotThrowAnyException();
	}

	@Test
	public void testForLargeWVal() {
		assertExpression("0/5 * * 32W 1 ?", "The 'W' option does not make sense with values larger than",
				"Expected ParseException did not fire for W with value larger than 31");
	}

	@Test
	public void testSecRangeIntervalAfterSlash() {
		// Test case 1
		assertExpression("/120 0 8-18 ? * 2-6", "Increment > 60 : 120",
				"Cron did not validate bad range interval in '_blank/xxx' form");
		// Test case 2
		assertExpression("0/120 0 8-18 ? * 2-6", "Increment > 60 : 120",
				"Cron did not validate bad range interval in in '0/xxx' form");
		// Test case 3
		assertExpression("/ 0 8-18 ? * 2-6", "'/' must be followed by an integer.",
				"Cron did not validate bad range interval in '_blank/_blank'");
		// Test case 4
		assertExpression("0/ 0 8-18 ? * 2-6", "'/' must be followed by an integer.",
				"Cron did not validate bad range interval in '0/_blank'");
	}

	@Test
	public void testMinRangeIntervalAfterSlash() {
		// Test case 1
		assertExpression("0 /120 8-18 ? * 2-6", "Increment > 60 : 120",
				"Cron did not validate bad range interval in '_blank/xxx' form");
		// Test case 2
		assertExpression("0 0/120 8-18 ? * 2-6", "Increment > 60 : 120",
				"Cron did not validate bad range interval in in '0/xxx' form");
		// Test case 3
		assertExpression("0 / 8-18 ? * 2-6", "'/' must be followed by an integer.",
				"Cron did not validate bad range interval in '_blank/_blank'");
		// Test case 4
		assertExpression("0 0/ 8-18 ? * 2-6", "'/' must be followed by an integer.",
				"Cron did not validate bad range interval in '0/_blank'");
	}

	@Test
	public void testHourRangeIntervalAfterSlash() {
		// Test case 1
		assertExpression("0 0 /120 ? * 2-6", "Increment > 24 : 120",
				"Cron did not validate bad range interval in '_blank/xxx' form");
		// Test case 2
		assertExpression("0 0 0/120 ? * 2-6", "Increment > 24 : 120",
				"Cron did not validate bad range interval in in '0/xxx' form");
		// Test case 3
		assertExpression("0 0 / ? * 2-6", "'/' must be followed by an integer.",
				"Cron did not validate bad range interval in '_blank/_blank'");
		// Test case 4
		assertExpression("0 0 0/ ? * 2-6", "'/' must be followed by an integer.",
				"Cron did not validate bad range interval in '0/_blank'");
	}

	@Test
	public void testDayOfMonthRangeIntervalAfterSlash() {
		// Test case 1
		assertExpression("0 0 0 /120 * 2-6", "Increment > 31 : 120",
				"Cron did not validate bad range interval in '_blank/xxx' form");
		// Test case 2
		assertExpression("0 0 0 0/120 * 2-6", "Increment > 31 : 120",
				"Cron did not validate bad range interval in in '0/xxx' form");
		// Test case 3
		assertExpression("0 0 0 / * 2-6", "'/' must be followed by an integer.",
				"Cron did not validate bad range interval in '_blank/_blank'");
		// Test case 4
		assertExpression("0 0 0 0/ * 2-6", "'/' must be followed by an integer.",
				"Cron did not validate bad range interval in '0/_blank'");
	}

	@Test
	public void testMonthRangeIntervalAfterSlash() {
		// Test case 1
		assertExpression("0 0 0 ? /120 2-6", "Increment > 12 : 120",
				"Cron did not validate bad range interval in '_blank/xxx' form");
		// Test case 2
		assertExpression("0 0 0 ? 0/120 2-6", "Increment > 12 : 120",
				"Cron did not validate bad range interval in in '0/xxx' form");
		// Test case 3
		assertExpression("0 0 0 ? / 2-6", "'/' must be followed by an integer.",
				"Cron did not validate bad range interval in '_blank/_blank'");
		// Test case 4
		assertExpression("0 0 0 ? 0/ 2-6", "'/' must be followed by an integer.",
				"Cron did not validate bad range interval in '0/_blank'");
	}

	@Test
	public void testDayOfWeekRangeIntervalAfterSlash() {
		// Test case 1
		assertExpression("0 0 0 ? * /120", "Increment > 7 : 120",
				"Cron did not validate bad range interval in '_blank/xxx' form");
		// Test case 2
		assertExpression("0 0 0 ? * 0/120", "Increment > 7 : 120",
				"Cron did not validate bad range interval in in '0/xxx' form");
		// Test case 3
		assertExpression("0 0 0 ? * /", "'/' must be followed by an integer.",
				"Cron did not validate bad range interval in '_blank/_blank'");
		// Test case 4
		assertExpression("0 0 0 ? * 0/", "'/' must be followed by an integer.",
				"Cron did not validate bad range interval in '0/_blank'");
	}

	private static void assertExpression(String expression, String messageContains, String as) {
		assertThatThrownBy(() -> {
			new QuartzCronExpression(expression);
		}).isInstanceOf(ParseException.class).hasMessageContaining(messageContains).as(as);
	}
}
