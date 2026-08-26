package com.yourcompany.salesagent.shared.api;

import java.util.LinkedHashMap;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.yourcompany.salesagent.customer.application.CustomerNotFoundException;
import com.yourcompany.salesagent.customer.application.CustomerValidationException;
import com.yourcompany.salesagent.ai.application.AiModelConnectionException;
import com.yourcompany.salesagent.ai.application.AiModelNotConfiguredException;
import com.yourcompany.salesagent.interaction.application.InteractionValidationException;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(AiModelNotConfiguredException.class)
	ProblemDetail handleAiModelNotConfigured(AiModelNotConfiguredException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
	}

	@ExceptionHandler(AiModelConnectionException.class)
	ProblemDetail handleAiModelConnection(AiModelConnectionException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, exception.getMessage());
	}

	@ExceptionHandler(CustomerNotFoundException.class)
	ProblemDetail handleNotFound(CustomerNotFoundException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
	}

	@ExceptionHandler(CustomerValidationException.class)
	ProblemDetail handleCustomerValidation(CustomerValidationException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
	}

	@ExceptionHandler(InteractionValidationException.class)
	ProblemDetail handleInteractionValidation(InteractionValidationException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "提交的客户信息不完整或格式不正确");
		var errors = new LinkedHashMap<String, String>();
		exception.getBindingResult().getFieldErrors().forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
		problem.setProperty("errors", errors);
		return problem;
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	ProblemDetail handleOptimisticLock(OptimisticLockingFailureException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "客户资料已被其他成员更新，请刷新后重试");
	}
}
