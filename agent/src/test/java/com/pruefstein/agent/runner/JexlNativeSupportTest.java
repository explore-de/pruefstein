package com.pruefstein.agent.runner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the registration itself, since nothing else can.
 * <p>
 * A JVM test run cannot notice the metadata missing, so deleting
 * {@link JexlNativeSupport} as dead code — which is exactly what it looks like
 * — would break every check in the native binary and no test would say a word
 * until someone ran the release. This at least makes that deletion fail.
 */
class JexlNativeSupportTest
{
	@Test
	void registersTheTypesJexlReachesThroughReflection()
	{
		// given the registration
		RegisterForReflection registration = JexlNativeSupport.class
			.getAnnotation(RegisterForReflection.class);
		assertNotNull(registration, "JexlNativeSupport exists only to carry this annotation");

		// when
		Set<Class<?>> targets = Set.of(registration.targets());

		// then — what Jackson builds from osquery's JSON, which is what every
		// expression walks: a list of maps
		assertTrue(targets.contains(ArrayList.class), "results.size() and results[0] land on ArrayList");
		assertTrue(targets.contains(LinkedHashMap.class), "results[0].column lands on LinkedHashMap");
		assertTrue(targets.contains(List.class));

		// and the types an expression compares those values against
		assertTrue(targets.contains(String.class));
		assertTrue(targets.contains(Integer.class), "Integer.parseInt is in the documented examples");
	}
}
