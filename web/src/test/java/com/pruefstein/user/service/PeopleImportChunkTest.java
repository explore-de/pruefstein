package com.pruefstein.user.service;

import java.nio.charset.Charset;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeopleImportChunkTest
{
	@Test
	void everyChunkOfALongCsvCarriesTheHeader()
	{
		// given
		StringBuilder csv = new StringBuilder("Vorname,Nachname,Mail\n");
		for (int i = 0; i < 1000; i++)
		{
			csv.append("First").append(i).append(",Last").append(i).append(",person").append(i).append("@example.com\n");
		}

		// when
		List<String> chunks = PeopleImport.chunks(csv.toString());

		// then
		assertTrue(chunks.size() > 1);
		chunks.forEach(chunk -> assertTrue(chunk.startsWith("Vorname,Nachname,Mail\n")));
		assertEquals(1000, chunks.stream().mapToInt(chunk -> PeopleImport.addresses(chunk).size()).sum());
	}

	@Test
	void aPlainListHasNoHeaderToRepeat()
	{
		// given
		String list = "a.b@example.com\n".repeat(1000);

		// when
		List<String> chunks = PeopleImport.chunks(list);

		// then
		assertTrue(chunks.size() > 1);
		assertTrue(chunks.get(1).startsWith("a.b@example.com"));
	}

	@Test
	void anExcelCsvInWindows1252KeepsItsUmlauts()
	{
		// given
		byte[] file = "Jürgen;Müller;j.mueller@example.com".getBytes(Charset.forName("windows-1252"));

		// when / then
		assertTrue(PeopleImport.decode(file).startsWith("Jürgen;Müller"));
	}

	@Test
	void aNameIsOnlyGuessedFromAnAddressThatPlainlyHasOne()
	{
		// given / when / then
		assertEquals("Jane", PeopleImport.fromAddress("jane.doe@example.com").firstname());
		assertEquals("Doe", PeopleImport.fromAddress("JANE.DOE@example.com").lastname());
		assertEquals("", PeopleImport.fromAddress("jdoe@example.com").firstname());
	}
}
