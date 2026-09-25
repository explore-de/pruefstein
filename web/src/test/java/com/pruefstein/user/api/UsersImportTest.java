package com.pruefstein.user.api;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.pruefstein.notification.ReportRequestMailService;
import com.pruefstein.user.domain.AppUser;
import com.pruefstein.user.repository.UserRepository;
import com.pruefstein.user.service.PeopleExtractionAiService;
import com.pruefstein.user.service.PeopleExtractionAiService.ExtractedPeople;
import com.pruefstein.user.service.PeopleExtractionAiService.ExtractedPerson;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Inviting a whole company from whatever list the admin has: the file is read
 * into a list to review, and only the reviewed list is mailed.
 */
@QuarkusTest
@TestSecurity(user = "admin", roles = { "admin" })
class UsersImportTest
{
	private static final String DOMAIN = "@import-test.example.com";

	@InjectMock
	PeopleExtractionAiService ai;

	@InjectMock
	ReportRequestMailService.Sender sender;

	@Inject
	UserRepository userRepository;

	@AfterEach
	void tearDown()
	{
		QuarkusTransaction.requiringNew().run(() -> userRepository.delete("mail like ?1", "%" + DOMAIN));
	}

	@Test
	void uploadingAFileShowsWhoWasFoundWithoutMailingAnyone()
	{
		// given an HR export, and a model that only names one of its two rows
		String csv = """
			Nachname;Vorname;E-Mail
			Doe;Jane;jane.doe%1$s
			Roe;Rick;rick.roe%1$s
			""".formatted(DOMAIN);
		when(ai.extract(anyString())).thenReturn(new ExtractedPeople(List.of(
			new ExtractedPerson("Jane", "Doe", "jane.doe" + DOMAIN),
			new ExtractedPerson("Invented", "Person", "ghost" + DOMAIN))));

		// when / then — the named row, the skipped row with a guessed name,
		// and never the address the model made up
		given()
			.multiPart("file", "staff.csv", csv.getBytes(StandardCharsets.UTF_8), "text/csv")
			.when().post("/Users/importFile")
			.then()
			.statusCode(200)
			.body(containsString("staff.csv"))
			.body(containsString("jane.doe" + DOMAIN))
			.body(containsString("value=\"Rick\""))
			.body(not(containsString("ghost" + DOMAIN)));
		Mockito.verifyNoInteractions(sender);
	}

	@Test
	void anUnreachableModelStillFindsEveryAddress()
	{
		// given
		when(ai.extract(anyString())).thenThrow(new RuntimeException("model unavailable"));

		// when / then
		given()
			.multiPart("file", "list.txt", ("jane.doe" + DOMAIN + ", ops" + DOMAIN).getBytes(StandardCharsets.UTF_8),
				"text/plain")
			.when().post("/Users/importFile")
			.then()
			.statusCode(200)
			.body(containsString("value=\"Jane\""))
			.body(containsString("ops" + DOMAIN));
	}

	@Test
	void peopleAlreadyOnRecordAreShownButNotOfferedAgain()
	{
		// given
		QuarkusTransaction.requiringNew().run(() -> {
			AppUser user = new AppUser();
			user.setFirstname("Jane");
			user.setLastname("Doe");
			user.setMail("jane.doe" + DOMAIN);
			userRepository.persist(user);
		});
		when(ai.extract(anyString())).thenReturn(new ExtractedPeople(List.of()));

		// when / then
		given()
			.multiPart("file", "list.txt", ("JANE.DOE" + DOMAIN).getBytes(StandardCharsets.UTF_8), "text/plain")
			.when().post("/Users/importFile")
			.then()
			.statusCode(200)
			.body(containsString("ALREADY A USER"))
			.body(not(containsString("name=\"mail\"")));
	}

	@Test
	void sendingTheReviewedListInvitesEveryoneOnItOnce()
	{
		// given the reviewed list, edited, with one address twice and one bad
		// when
		given()
			.contentType("application/x-www-form-urlencoded")
			.formParam("firstname", "Jane", "Rick", "Jane", "Bad")
			.formParam("lastname", "Doe", "Roe", "Doe", "Address")
			.formParam("mail", "jane.doe" + DOMAIN, "rick.roe" + DOMAIN, "Jane.Doe" + DOMAIN, "not-an-address")
			.when().post("/Users/inviteAll")
			.then()
			.statusCode(lessThan(400));

		// then
		QuarkusTransaction.requiringNew().run(() -> {
			assertEquals(2, userRepository.count("mail like ?1", "%" + DOMAIN));
			AppUser rick = userRepository.find("mail", "rick.roe" + DOMAIN).firstResult();
			assertEquals("Rick", rick.getFirstname());
			assertTrue(rick.getOidcSubject() == null);
		});
		Mockito.verify(sender).send(eq("jane.doe" + DOMAIN), anyString(), any());
		Mockito.verify(sender).send(eq("rick.roe" + DOMAIN), anyString(), any());
		Mockito.verifyNoMoreInteractions(sender);
	}

	@Test
	@TestSecurity(user = "alice", roles = {})
	void nonAdminCannotImport()
	{
		// given / when / then
		given()
			.multiPart("file", "list.txt", ("x" + DOMAIN).getBytes(StandardCharsets.UTF_8), "text/plain")
			.when().post("/Users/importFile")
			.then()
			.statusCode(403);
		given()
			.contentType("application/x-www-form-urlencoded")
			.formParam("firstname", "X")
			.formParam("lastname", "Y")
			.formParam("mail", "x" + DOMAIN)
			.when().post("/Users/inviteAll")
			.then()
			.statusCode(403);
	}
}
