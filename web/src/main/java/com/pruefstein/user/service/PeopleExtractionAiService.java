package com.pruefstein.user.service;

import java.util.List;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface PeopleExtractionAiService
{
	@SystemMessage(fromResource = "prompts/extract-people-system.txt")
	@UserMessage("""
		Extract every person from this excerpt of an uploaded file:

		{text}
		""")
	ExtractedPeople extract(String text);

	record ExtractedPeople(List<ExtractedPerson> people)
	{
		public List<ExtractedPerson> peopleOrEmpty()
		{
			return people == null ? List.of() : people;
		}
	}

	record ExtractedPerson(String firstname, String lastname, String mail)
	{
	}
}
