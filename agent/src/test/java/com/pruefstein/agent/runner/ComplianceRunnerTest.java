package com.pruefstein.agent.runner;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import com.pruefstein.agent.client.CheckItem;
import com.pruefstein.agent.client.ResultPayload;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComplianceRunnerTest
{
	/**
	 * A latch rather than a stopwatch: every task waits for all the others to
	 * arrive, so this can only pass if they really were in flight together —
	 * and it says so without depending on how fast the machine is. Run
	 * sequentially, the first task waits out its timeout and reports a failure.
	 */
	@Test
	void runsChecksAtTheSameTime()
	{
		ComplianceRunner runner = new ComplianceRunner();
		runner.parallelism = 100;
		CountDownLatch started = new CountDownLatch(8);

		List<ResultPayload> results = runner.runChecks(checks(8),
			check -> new ResultPayload(check.id(), awaitEveryone(started, 5_000), null));

		assertTrue(results.stream().allMatch(ResultPayload::passed),
			"all 8 checks should have been in flight at once");
		assertEquals(ids(8), itemIds(results), "results should stay in the order the server sent");
	}

	/**
	 * The report keys results by check, but a list that reshuffles itself per
	 * run makes two reports of the same machine needlessly hard to compare.
	 */
	@Test
	void keepsTheServersOrderWhateverFinishesFirst()
	{
		ComplianceRunner runner = new ComplianceRunner();
		runner.parallelism = 100;

		// Last check finishes first: each one waits for the one after it
		CountDownLatch[] finished = IntStream.rangeClosed(0, 6)
			.mapToObj(i -> new CountDownLatch(1))
			.toArray(CountDownLatch[]::new);
		List<ResultPayload> results = runner.runChecks(checks(6), check -> {
			int id = check.id().intValue();
			boolean inTurn = id == 6 || await(finished[id + 1]);
			finished[id].countDown();
			return new ResultPayload(check.id(), inTurn, null);
		});

		assertTrue(results.stream().allMatch(ResultPayload::passed), "checks should have finished last to first");
		assertEquals(ids(6), itemIds(results));
	}

	@Test
	void neverExceedsTheConfiguredParallelism()
	{
		ComplianceRunner runner = new ComplianceRunner();
		runner.parallelism = 2;
		CountDownLatch started = new CountDownLatch(4);

		List<ResultPayload> results = runner.runChecks(checks(4),
			check -> new ResultPayload(check.id(), awaitEveryone(started, 300), null));

		// The first two cannot see the other two start, because they hold both
		// of the two slots until they give up waiting.
		assertFalse(results.get(0).passed(), "check 1 should not have seen all four running");
		assertFalse(results.get(1).passed(), "check 2 should not have seen all four running");
		assertEquals(ids(4), itemIds(results));
	}

	/**
	 * A nonsensical limit has to degrade to running one at a time rather than
	 * throwing out of {@code Executors.newFixedThreadPool}.
	 */
	@Test
	void survivesAParallelismOfZero()
	{
		ComplianceRunner runner = new ComplianceRunner();
		runner.parallelism = 0;

		List<ResultPayload> results = runner.runChecks(checks(3),
			check -> new ResultPayload(check.id(), true, null));

		assertEquals(ids(3), itemIds(results));
	}

	/**
	 * The line someone acts on: what is broken, how long they have, and what
	 * happens if they leave it.
	 */
	@Test
	void theNoticeSaysWhatAFailureCostsAndWhen()
	{
		Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS);
		String expected = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
			.withZone(ZoneId.systemDefault()).format(deadline);

		String notice = ComplianceRunner.remediationNotice(3, deadline);

		assertTrue(notice.startsWith("3 checks are still failing. Fix them"), notice);
		assertTrue(notice.contains("by " + expected + " (7 days)"), notice);
		assertTrue(notice.endsWith("or this report is recorded as non-compliant."), notice);
	}

	@Test
	void theNoticeCountsOneFailureInTheSingular()
	{
		String notice = ComplianceRunner.remediationNotice(1, Instant.now().plus(47, ChronoUnit.HOURS));

		assertTrue(notice.startsWith("1 check is still failing. Fix it"), notice);
		// Part days round up, exactly as the reminder mail counts them, so the
		// two never quote a different number on the same day
		assertTrue(notice.contains("(2 days)"), notice);
	}

	/** A deadline inside the last day has to read as something, not "0 days". */
	@Test
	void theNoticeCallsTheLastDayToday()
	{
		String notice = ComplianceRunner.remediationNotice(1, Instant.now().plusSeconds(600));

		assertTrue(notice.contains("(today)"), notice);
	}

	/**
	 * A clean run is decided on arrival and carries no deadline, so there is
	 * nothing to warn about — and nothing should be printed.
	 */
	@Test
	void thereIsNoNoticeWithoutADeadline()
	{
		assertNull(ComplianceRunner.remediationNotice(0, null));
		assertNull(ComplianceRunner.remediationNotice(3, null));
		assertNull(ComplianceRunner.remediationNotice(0, Instant.now().plus(7, ChronoUnit.DAYS)));
	}

	private static List<CheckItem> checks(int count)
	{
		return IntStream.rangeClosed(1, count)
			.mapToObj(i -> new CheckItem((long) i, "check " + i, "SELECT " + i + ";", "true"))
			.toList();
	}

	private static List<Long> ids(int count)
	{
		return IntStream.rangeClosed(1, count).mapToObj(Long::valueOf).toList();
	}

	private static List<Long> itemIds(List<ResultPayload> results)
	{
		return results.stream().map(ResultPayload::itemId).toList();
	}

	private static boolean awaitEveryone(CountDownLatch started, long timeoutMillis)
	{
		started.countDown();
		try
		{
			return started.await(timeoutMillis, TimeUnit.MILLISECONDS);
		}
		catch (InterruptedException _)
		{
			Thread.currentThread().interrupt();
			return false;
		}
	}

	private static boolean await(CountDownLatch latch)
	{
		try
		{
			return latch.await(5, TimeUnit.SECONDS);
		}
		catch (InterruptedException _)
		{
			Thread.currentThread().interrupt();
			return false;
		}
	}
}
