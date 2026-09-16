package com.pruefstein.agent.runner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Keeps JEXL working in the native binary.
 * <p>
 * A check's expression is authored by an administrator and evaluated here
 * against whatever osquery returned, so JEXL reaches every value by reflection:
 * {@code results.size()} is a reflective call on the list Jackson built,
 * {@code results[0]} another, and {@code results[0].value} a lookup on the map
 * inside it. None of those call sites exist in the code, so native-image cannot
 * see them, drops the metadata, and every lookup quietly returns {@code null} —
 * which surfaces as "JEXL error : &gt; error caused by null operand" on the
 * first comparison and turns every check into an error. The JVM build has no
 * such problem, which is what makes this easy to miss.
 * <p>
 * Registering the concrete types Jackson produces, and the interfaces JEXL may
 * resolve against, puts the metadata back. The boxed types and {@code String}
 * are here because an expression compares against them, and {@code Integer} in
 * particular is called directly — {@code Integer.parseInt(results[0].value)} is
 * in the documented examples.
 */
@RegisterForReflection(targets = {
	ArrayList.class,
	LinkedHashMap.class,
	HashMap.class,
	List.class,
	Map.class,
	String.class,
	Integer.class,
	Long.class,
	Double.class,
	Boolean.class,
})
public final class JexlNativeSupport
{
	private JexlNativeSupport()
	{
	}
}
