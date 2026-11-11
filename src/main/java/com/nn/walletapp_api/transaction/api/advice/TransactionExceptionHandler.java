package com.nn.walletapp_api.transaction.api.advice;

import com.nn.walletapp_api.transaction.exception.DuplicateTransactionException;
import com.nn.walletapp_api.transaction.exception.InvalidTransactionStateException;
import com.nn.walletapp_api.transaction.exception.SelfTransferException;
import com.nn.walletapp_api.transaction.exception.TransactionNotFoundException;
import com.nn.walletapp_api.transaction.exception.TransactionOwnershipException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;

@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class TransactionExceptionHandler {

    private static final String BASE_ERROR_URI = "https://api.walletapp.com/errors/";
    private final Clock clock;

    @ExceptionHandler(DuplicateTransactionException.class)
    public ProblemDetail handleDuplicateTransaction(DuplicateTransactionException ex, WebRequest request) {
        log.warn("Duplicate transaction rejected: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        enrichProblemDetail(problem, "duplicate-transaction", "Duplicate Transaction", request);
        return problem;
    }

    @ExceptionHandler(InvalidTransactionStateException.class)
    public ProblemDetail handleInvalidTransactionState(InvalidTransactionStateException ex, WebRequest request) {
        log.warn("Invalid transaction state transition: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        enrichProblemDetail(problem, "invalid-transaction-state", "Invalid Transaction State", request);
        return problem;
    }

    @ExceptionHandler(SelfTransferException.class)
    public ProblemDetail handleSelfTransfer(SelfTransferException ex, WebRequest request) {
        log.warn("Business rule violation (self-transfer attempt): {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_CONTENT,
                ex.getMessage()
        );
        enrichProblemDetail(problem, "self-transfer-not-allowed", "Self Transfer Not Allowed", request);
        return problem;
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ProblemDetail handleTransactionNotFound(TransactionNotFoundException ex, WebRequest request) {
        log.warn("Transaction not found: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        enrichProblemDetail(problem, "transaction-not-found", "Transaction Not Found", request);
        return problem;
    }

    @ExceptionHandler(TransactionOwnershipException.class)
    public ProblemDetail handleTransactionOwnershipViolation(TransactionOwnershipException ex, WebRequest request) {
        log.warn("IDOR / Transaction ownership violation detected: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "You do not have permission to access this transaction"
        );
        enrichProblemDetail(problem, "transaction-ownership-violation", "Transaction Ownership Violation", request);
        return problem;
    }

    private void enrichProblemDetail(ProblemDetail problem, String typeSuffix, String title, WebRequest request) {
        problem.setTitle(title);
        problem.setType(URI.create(BASE_ERROR_URI + typeSuffix));
        problem.setProperty("timestamp", Instant.now(clock).toString());

        if (request instanceof ServletWebRequest servletWebRequest) {
            problem.setInstance(URI.create(servletWebRequest.getRequest().getRequestURI()));
        }
    }
}