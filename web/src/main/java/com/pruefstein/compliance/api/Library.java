package com.pruefstein.compliance.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.pruefstein.compliance.domain.ComplianceItem;
import com.pruefstein.compliance.library.ComplianceLibrary;
import com.pruefstein.compliance.library.LibraryEntry;
import com.pruefstein.compliance.library.LibraryInstantiator;
import com.pruefstein.compliance.repository.ComplianceItemRepository;
import io.quarkiverse.renarde.Controller;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.POST;
import org.jboss.resteasy.reactive.RestForm;

/**
 * The built-in library of checks, and the way to put one back into force.
 * <p>
 * Every entry is seeded once on install; this screen is for everything after
 * that — re-adding a check that was retired, or adding one a later release
 * brought after an admin had already curated the catalogue. An entry is offered
 * only while no check created from it is in force, so adding it twice cannot
 * happen by accident.
 */
@RolesAllowed("**")
public class Library extends Controller
{
	/** What a check without a group is listed under. */
	private static final String UNGROUPED = "Blocked Apps";

	@Inject
	ComplianceLibrary complianceLibrary;

	@Inject
	LibraryInstantiator instantiator;

	@Inject
	ComplianceItemRepository itemRepository;

	@CheckedTemplate
	public static class Templates
	{
		private Templates()
		{
		}

		public static native TemplateInstance index(List<Section> sections);
	}

	public record Section(String name, List<Row> rows)
	{
	}

	/**
	 * @param inUse
	 *            the check in force created from this entry, if there is one
	 */
	public record Row(LibraryEntry entry, ComplianceItem inUse)
	{
		public boolean isAvailable()
		{
			return inUse == null;
		}

		/** Where the check in force is managed. */
		public boolean isInGroup()
		{
			return inUse != null && inUse.getGroup() != null;
		}
	}

	public TemplateInstance index()
	{
		Map<String, List<Row>> sections = new LinkedHashMap<>();
		for (LibraryEntry entry : complianceLibrary.entries())
		{
			ComplianceItem inUse = itemRepository.findActiveByLibraryKey(entry.key()).orElse(null);
			sections.computeIfAbsent(entry.group() != null ? entry.group() : UNGROUPED, name -> new ArrayList<>())
				.add(new Row(entry, inUse));
		}
		return Templates.index(sections.entrySet().stream()
			.map(section -> new Section(section.getKey(), section.getValue()))
			.toList());
	}

	@POST
	@Transactional
	@RolesAllowed("${pruefstein.security.admin-role:admin}")
	public void add(@RestForm @NotBlank String key)
	{
		if (validationFailed())
		{
			index();
			return;
		}
		Optional<LibraryEntry> entry = complianceLibrary.find(key);
		if (entry.isEmpty())
		{
			notFound();
			return;
		}
		if (instantiator.isInUse(entry.get()))
		{
			flash("message", "“" + entry.get().name() + "” is already in force; nothing was added.");
			index();
			return;
		}
		ComplianceItem item = instantiator.instantiate(entry.get());
		flash("message", "Added “" + item.getName() + "”"
			+ (item.getGroup() != null ? " to “" + item.getGroup().getName() + "”" : "")
			+ ". Agents run it from their next report.");
		index();
	}
}
