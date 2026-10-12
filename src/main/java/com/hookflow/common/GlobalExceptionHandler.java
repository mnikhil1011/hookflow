package com.hookflow.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions into RFC 9457 "problem detail" JSON responses.
 * The base class already covers Spring's own errors, such as malformed JSON or a bad path variable.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(NotFoundException.class)
	ProblemDetail handleNotFound(NotFoundException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler(ConflictException.class)
	ProblemDetail handleConflict(ConflictException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
	}

	@ExceptionHandler(InvalidRequestException.class)
	ProblemDetail handleInvalidRequest(InvalidRequestException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	@ExceptionHandler(PayloadTooLargeException.class)
	ProblemDetail handlePayloadTooLarge(PayloadTooLargeException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, e.getMessage());
	}

	@ExceptionHandler({ DataAccessResourceFailureException.class, CannotCreateTransactionException.class })
	ProblemDetail handleDatabaseUnavailable(Exception e) {
		logger.error("Database unavailable", e);
		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "The service is temporarily unavailable");
	}

	/** Adds a field-by-field list of what failed validation, so clients can see what to fix. */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> errors = new LinkedHashMap<>();
		e.getBindingResult().getFieldErrors()
				.forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "The request body is invalid");
		problem.setProperty("errors", errors);
		return ResponseEntity.status(status).headers(headers).body(problem);
	}

}
