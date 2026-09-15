package com.pruefstein.onboarding;

/**
 * One step of the agent walkthrough: what it is called, the command to run, and
 * the sentence explaining what it does.
 *
 * @param command
 *            shown verbatim in a monospace block, so it has to be complete
 *            enough to paste into a terminal without editing.
 */
public record SetupStep(String title, String command, String note)
{
}
