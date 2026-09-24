package com.pruefstein.mcp;

import java.util.List;

import com.pruefstein.compliance.domain.AppBlacklistCheck;
import com.pruefstein.compliance.domain.ComplianceGroup;
import com.pruefstein.compliance.domain.ComplianceItem;
import com.pruefstein.compliance.domain.ExpressionCheck;
import com.pruefstein.compliance.repository.ComplianceGroupRepository;
import com.pruefstein.compliance.repository.ComplianceItemRepository;
import com.pruefstein.compliance.service.CheckResolver;
import com.pruefstein.compliance.service.CheckResolver.ResolvedCheck;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotBlank;

/**
 * The compliance catalogue over MCP, with the permissions of the compliance
 * screens: anyone signed in reads it, only an admin adds to it.
 */
@RolesAllowed("**")
public class ComplianceTools
{
	@Inject
	ComplianceGroupRepository groupRepository;

	@Inject
	ComplianceItemRepository itemRepository;

	@Inject
	CheckResolver checkResolver;

	public record GroupDto(Long id, String name, int checks)
	{
	}

	public record Groups(List<GroupDto> groups)
	{
	}

	/**
	 * @param editable
	 *            false for a generated check, whose query is derived and cannot
	 *            be edited directly
	 */
	public record CheckDto(Long id, String name, Long groupId, String group, String control, String query,
		String expectedExpression, boolean editable)
	{
	}

	public record Checks(List<CheckDto> checks)
	{
	}

	@Tool(description = "Lists the compliance groups in force (ISO 27001 control families), with how many checks each holds.", structuredContent = true)
	Groups listComplianceGroups()
	{
		return new Groups(groupRepository.listActive().stream()
			.map(group -> new GroupDto(group.id, group.getName(), checksOf(group).size()))
			.toList());
	}

	@Tool(description = "Lists the compliance checks in force with the osquery SQL each runs on a device and the "
		+ "JEXL expression its rows must satisfy to pass.", structuredContent = true)
	Checks listComplianceItems(
		@ToolArg(description = "Only the checks in this group; every group when left out", required = false) Long groupId)
	{
		List<ComplianceItem> items;
		if (groupId == null)
		{
			items = itemRepository.listActive().stream()
				.filter(check -> !(check instanceof AppBlacklistCheck))
				.toList();
		}
		else
		{
			items = checksOf(activeGroup(groupId));
		}
		return new Checks(items.stream().map(this::toDto).toList());
	}

	@Tool(description = "Reads one compliance check by its id.", structuredContent = true)
	CheckDto getComplianceItem(@ToolArg(description = "The check's id") Long id)
	{
		ComplianceItem item = itemRepository.findById(id);
		if (item == null || item.isRetired())
		{
			throw new ToolCallException("No compliance check in force with id " + id);
		}
		return toDto(item);
	}

	@Tool(description = "Creates a compliance group to file checks under. Admins only.", structuredContent = true)
	@RolesAllowed("${pruefstein.security.admin-role:admin}")
	@Transactional
	GroupDto createComplianceGroup(@ToolArg(description = "The group's name") @NotBlank String name)
	{
		ComplianceGroup group = new ComplianceGroup();
		group.setName(name);
		groupRepository.persist(group);
		return new GroupDto(group.id, group.getName(), 0);
	}

	@Tool(description = "Adds a compliance check to a group. Agents run it from their next report. Admins only.", structuredContent = true)
	@RolesAllowed("${pruefstein.security.admin-role:admin}")
	@Transactional
	CheckDto createComplianceItem(
		@ToolArg(description = "The group to add the check to") Long groupId,
		@ToolArg(description = "What the check verifies, as a person would say it") @NotBlank String name,
		@ToolArg(description = "The osquery SQL the agent runs on the device") @NotBlank String query,
		@ToolArg(description = "The JEXL expression over the query's rows that must be true to pass, "
			+ "e.g. results.size() > 0") @NotBlank String expectedExpression)
	{
		ExpressionCheck item = new ExpressionCheck();
		item.setName(name);
		item.setQuery(query);
		item.setExpectedExpression(expectedExpression);
		item.setGroup(activeGroup(groupId));
		itemRepository.persist(item);
		return toDto(item);
	}

	private ComplianceGroup activeGroup(Long id)
	{
		ComplianceGroup group = groupRepository.findById(id);
		if (group == null || group.isRetired())
		{
			throw new ToolCallException("No compliance group in force with id " + id);
		}
		return group;
	}

	/**
	 * The checks a group shows. The blacklist check is managed on the blocked
	 * apps screen instead, as on the group screen.
	 */
	private List<ComplianceItem> checksOf(ComplianceGroup group)
	{
		return itemRepository.listActive(group).stream()
			.filter(check -> !(check instanceof AppBlacklistCheck))
			.toList();
	}

	private CheckDto toDto(ComplianceItem item)
	{
		ResolvedCheck resolved = checkResolver.resolve(item);
		ComplianceGroup group = item.getGroup();
		return new CheckDto(item.id, item.getName(), group != null ? group.id : null,
			group != null ? group.getName() : null, item.getControl(), resolved.query(), resolved.expression(),
			item.isEditable());
	}
}
