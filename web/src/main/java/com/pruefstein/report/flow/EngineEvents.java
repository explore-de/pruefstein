package com.pruefstein.report.flow;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.events.EventPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Hands CloudEvents to the workflow engine in-process, with no broker in
 * between.
 *
 * <p>
 * With no {@code EventConsumer} or {@code EventPublisher} CDI bean on the
 * classpath, the engine falls back to {@code InMemoryEvents} — a single object
 * that is both its consumer and a publisher. Publishing to it delivers straight
 * to every workflow instance listening for that event type.
 *
 * <p>
 * Events are therefore only as durable as the JVM. One published while the
 * process is shutting down is lost, and the workflow waiting for it stays
 * parked until something else moves it along.
 */
@ApplicationScoped
public class EngineEvents
{
	private static final URI SOURCE = URI.create("pruefstein-web");

	@Inject
	WorkflowApplication application;

	/**
	 * Blocks until the engine has taken the event, mirroring the
	 * {@code sendAndAwait} this replaced: a failure has to surface to the
	 * caller rather than vanish into a background thread.
	 */
	public void publish(String type, String flowInstanceId, String dataJson)
	{
		CloudEvent event = CloudEventBuilder.v1()
			.withId(UUID.randomUUID().toString())
			.withType(type)
			.withSource(SOURCE)
			.withTime(OffsetDateTime.now(ZoneOffset.UTC))
			.withExtension("flowinstanceid", flowInstanceId)
			.withDataContentType("application/json")
			.withData(dataJson.getBytes(StandardCharsets.UTF_8))
			.build();

		publisher().publish(event).join();
	}

	/**
	 * The engine's consumer doubles as the in-process publisher. Anything else
	 * means a broker-backed consumer got bound after all, and publishing here
	 * would never reach the waiting workflow — so fail loudly rather than drop
	 * the event.
	 */
	private EventPublisher publisher()
	{
		if (application.eventConsumer() instanceof EventPublisher publisher)
		{
			return publisher;
		}
		throw new IllegalStateException(
			"Workflow engine consumer %s is not an in-process publisher; events cannot be delivered"
				.formatted(application.eventConsumer().getClass().getName()));
	}
}
