package com.pruefstein.report.service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import com.pruefstein.report.flow.PeriodicReportingFlow;
import io.quarkiverse.flow.persistence.jpa.WorkflowInstanceEntity;
import io.quarkiverse.flow.persistence.jpa.WorkflowInstanceRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowStatus;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A parked instance holds an event registration for the life of the JVM, so
 * discarding one has to actually detach it from the engine rather than just
 * tidy the database.
 *
 * <p>
 * Exercised against the periodic reporting flow, which since reports stopped
 * waiting for an outcome event is the only flow there is.
 */
@QuarkusTest
class WorkflowInstancesTest
{
	@Inject
	WorkflowInstances workflowInstances;

	@Inject
	PeriodicReportingFlow periodicReportingFlow;

	@Inject
	WorkflowInstanceRepository instanceRepository;

	@Test
	void discardingAParkedInstanceDetachesItFromTheEngine()
	{
		// given — an instance parked on its listen task
		WorkflowInstance instance = periodicReportingFlow.instance(Map.of("deviceId", "instances-test-1"));
		instance.start();
		String instanceId = instance.id();
		assertTrue(periodicReportingFlow.definition().activeInstance(instanceId).isPresent(),
			"instance should be active before it is discarded");

		// when
		QuarkusTransaction.requiringNew()
			.run(() -> workflowInstances.discard(periodicReportingFlow, instanceId));

		// then — cancelling unwinds the parked listen task on an engine thread,
		// which is what releases its event registration. That propagates
		// through the listen task's future, not inline.
		await("a discarded instance should no longer be held by the engine")
			.atMost(Duration.ofSeconds(5))
			.until(() -> periodicReportingFlow.definition().activeInstance(instanceId).isEmpty());
	}

	@Test
	void discardingAnUnknownInstanceIsHarmless()
	{
		// given — the shape of a leftover from a previous JVM
		String instanceId = "no-such-instance";
		assertTrue(periodicReportingFlow.definition().activeInstance(instanceId).isEmpty());

		// when / then — no row to remove, and nothing blows up
		QuarkusTransaction.requiringNew()
			.run(() -> workflowInstances.discard(periodicReportingFlow, instanceId));
		assertEquals(0, instanceRepository.count("instanceId", instanceId));
	}

	@Test
	void theSweepRemovesUnfinishedInstancesFromAnEarlierRun()
	{
		// given — a row nothing in this process holds, as after a restart
		String orphan = persistRow("swept-orphan", WorkflowStatus.WAITING, Instant.now().minus(1, ChronoUnit.DAYS));

		// when
		long removed = QuarkusTransaction.requiringNew().call(() -> workflowInstances.sweep(Instant.now()));

		// then
		assertTrue(removed >= 1);
		assertEquals(0, instanceRepository.count("instanceId", orphan));
	}

	@Test
	void theSweepKeepsInstancesTheEngineAlreadyFinishedWith()
	{
		// given — a terminal row is the record of what happened, not a leftover
		String completed = persistRow("swept-completed", WorkflowStatus.COMPLETED,
			Instant.now().minus(1, ChronoUnit.DAYS));

		// when
		QuarkusTransaction.requiringNew().call(() -> workflowInstances.sweep(Instant.now()));

		// then
		assertEquals(1, instanceRepository.count("instanceId", completed));
	}

	@Test
	void theSweepSparesAnInstanceThisProcessIsStillHolding()
	{
		// given — parked right now, and backdated so only the engine check can
		// save it from the sweep
		WorkflowInstance instance = periodicReportingFlow.instance(Map.of("deviceId", "instances-test-2"));
		instance.start();
		String instanceId = instance.id();
		QuarkusTransaction.requiringNew().run(() -> instanceRepository
			.update("startedAt = ?1 where instanceId = ?2", Instant.now().minus(1, ChronoUnit.DAYS), instanceId));

		// when
		QuarkusTransaction.requiringNew().call(() -> workflowInstances.sweep(Instant.now()));

		// then
		assertEquals(1, instanceRepository.count("instanceId", instanceId));
		assertTrue(periodicReportingFlow.definition().activeInstance(instanceId).isPresent());
	}

	/** A row as an earlier process would have left it behind. */
	private String persistRow(String instanceId, WorkflowStatus status, Instant startedAt)
	{
		QuarkusTransaction.requiringNew().run(() -> {
			WorkflowInstanceEntity entity = new WorkflowInstanceEntity(
				"pruefstein-web", periodicReportingFlow.definition().id(), instanceId, startedAt, null);
			entity.setStatus(status);
			instanceRepository.persist(entity);
		});
		return instanceId;
	}

	@Test
	void discardingNothingIsHarmless()
	{
		assertDoesNotThrow(() -> QuarkusTransaction.requiringNew().run(() -> {
			workflowInstances.discard(periodicReportingFlow, null);
			workflowInstances.discard(periodicReportingFlow, "  ");
		}));
	}
}
