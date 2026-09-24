package com.pruefstein.agent.runner;

/** {@code osqueryi} could not answer a query: missing, hung, or failed. */
class OsqueryException extends RuntimeException
{
	OsqueryException(String message)
	{
		super(message);
	}

	OsqueryException(String message, Throwable cause)
	{
		super(message, cause);
	}
}
