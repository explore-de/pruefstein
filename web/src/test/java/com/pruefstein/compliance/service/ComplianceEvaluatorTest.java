package com.pruefstein.compliance.service;

import java.util.stream.Stream;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@QuarkusTest
class ComplianceEvaluatorTest
{
	@Inject
	ComplianceEvaluator evaluator;

	static Stream<Arguments> passingExpressions()
	{
		return Stream.of(
			arguments("[{\"encrypted\":\"1\"}]", "results[0].encrypted == \"1\""),
			arguments("[{\"pid\":\"1\"},{\"pid\":\"2\"}]", "results.size() == 2"),
			arguments("[]", "results.isEmpty()"),
			arguments("[{\"status\":\"on\"},{\"status\":\"on\"}]",
				"results.size() > 0 && results[0].status == \"on\""));
	}

	@ParameterizedTest(name = "{1}")
	@MethodSource("passingExpressions")
	void returnsTrueWhenExpressionPasses(String json, String expression) throws Exception
	{
		// when
		boolean result = evaluator.evaluate(json, expression);

		// then
		assertTrue(result);
	}

	@Test
	void returnsFalseWhenExpressionFails() throws Exception
	{
		// given
		String json = "[{\"encrypted\":\"0\"}]";

		// when
		boolean result = evaluator.evaluate(json, "results[0].encrypted == \"1\"");

		// then
		assertFalse(result);
	}

	@Test
	void throwsWhenExpressionReturnsNonBoolean()
	{
		// given
		String json = "[{\"count\":\"5\"}]";

		// when / then
		assertThrows(IllegalArgumentException.class,
			() -> evaluator.evaluate(json, "results.size()"));
	}

	@Test
	void throwsOnInvalidJson()
	{
		// given
		String invalidJson = "not-json";

		// when / then
		assertThrows(Exception.class,
			() -> evaluator.evaluate(invalidJson, "results.isEmpty()"));
	}
}
