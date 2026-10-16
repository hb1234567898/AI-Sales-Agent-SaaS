package com.yourcompany.salesagent.assistant.application;

public class AssistantWorkflowException extends RuntimeException {

	public AssistantWorkflowException(String message) {
		super(message);
	}

	public AssistantWorkflowException(String message, Throwable cause) {
		super(message, cause);
	}
}
