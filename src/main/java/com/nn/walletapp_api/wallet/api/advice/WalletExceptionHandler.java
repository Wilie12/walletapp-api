package com.nn.walletapp_api.wallet.api.advice;

import com.nn.walletapp_api.wallet.exception.InsufficientFundsException;
import com.nn.walletapp_api.wallet.exception.InvalidAmountException;
import com.nn.walletapp_api.wallet.exception.WalletLockedException;
import com.nn.walletapp_api.wallet.exception.WalletNotFoundException;
import com.nn.walletapp_api.wallet.exception.WalletOwnershipException;
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
public class WalletExceptionHandler {

    private static final String BASE_ERROR_URI = "https://api.walletapp.com/errors/";
    private final Clock clock;

    @ExceptionHandler(WalletNotFoundException.class)
    public ProblemDetail handleWalletNotFound(WalletNotFoundException ex, WebRequest request) {
        log.warn("Wallet not found: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        enrichProblemDetail(problem, "wallet-not-found", "Wallet Not Found", request);
        return problem;
    }

    @ExceptionHandler(WalletOwnershipException.class)
    public ProblemDetail handleWalletOwnershipViolation(WalletOwnershipException ex, WebRequest request) {
        log.warn("IDOR / Wallet ownership violation detected: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "You do not have permission to access or modify this wallet"
        );
        enrichProblemDetail(problem, "wallet-ownership-violation", "Wallet Ownership Violation", request);
        return problem;
    }

    @ExceptionHandler(WalletLockedException.class)
    public ProblemDetail handleWalletLocked(WalletLockedException ex, WebRequest request) {
        log.warn("Operation rejected due to locked wallet state: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        enrichProblemDetail(problem, "wallet-locked", "Wallet Locked", request);
        return problem;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalWalletState(IllegalStateException ex, WebRequest request) {
        log.warn("Illegal wallet state transition: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        enrichProblemDetail(problem, "wallet-state-conflict", "Wallet State Conflict", request);
        return problem;
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ProblemDetail handleInsufficientFunds(InsufficientFundsException ex, WebRequest request) {
        log.warn("Operation rejected due to insufficient funds: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_CONTENT,
                ex.getMessage()
        );
        enrichProblemDetail(problem, "insufficient-funds", "Insufficient Funds", request);
        return problem;
    }

    @ExceptionHandler(InvalidAmountException.class)
    public ProblemDetail handleInvalidAmount(InvalidAmountException ex, WebRequest request) {
        log.warn("Invalid monetary amount provided: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        enrichProblemDetail(problem, "invalid-amount", "Invalid Amount", request);
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