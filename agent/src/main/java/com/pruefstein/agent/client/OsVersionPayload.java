package com.pruefstein.agent.client;

/**
 * The operating system the device was running when it was checked.
 *
 * @param name
 *            what osquery calls the OS, e.g. {@code macOS}
 * @param version
 *            the marketing version, e.g. {@code 15.7.9}
 * @param build
 *            the build identifier, e.g. {@code 24G830}
 * @param platform
 *            osquery's platform token, e.g. {@code darwin}
 */
public record OsVersionPayload(String name, String version, String build, String platform)
{
}
