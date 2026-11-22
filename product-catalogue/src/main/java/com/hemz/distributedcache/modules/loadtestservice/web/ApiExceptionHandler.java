package com.hemz.distributedcache.modules.loadtestservice.web;

import com.hemz.distributedcache.modules.loadtestservice.run.RunConflictException;
import com.hemz.distributedcache.modules.loadtestservice.run.RunNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Errors from the load tester's controllers only (all of them live in this package).
 * Product and /api/cache errors: ProductApiExceptionHandler. One advice per area, so each error has one handler.
 */
@RestControllerAdvice(basePackageClasses = ApiExceptionHandler.class)
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(RunConflictException.class)
    public ProblemDetail conflict(RunConflictException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        p.setTitle("Run conflict");
        return p;
    }

    @ExceptionHandler(RunNotFoundException.class)
    public ProblemDetail notFound(RunNotFoundException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        p.setTitle("Run not found");
        return p;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        p.setTitle("Invalid request");
        return p;
    }
}
