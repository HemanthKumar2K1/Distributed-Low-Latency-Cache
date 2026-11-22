package com.hemz.distributedcache.modules.productservice.web;

import com.hemz.distributedcache.modules.productservice.ProductServiceApplication;
import com.hemz.distributedcache.modules.productservice.service.ProductNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Errors from the product and /api/cache controllers only (everything under productservice).
 * The load tester has its own, ApiExceptionHandler: one advice per area, so each error has exactly one handler.
 */
@RestControllerAdvice(basePackageClasses = ProductServiceApplication.class)
public class ProductApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail notFound(ProductNotFoundException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        p.setTitle("Product not found");
        return p;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail invalidParameter(ConstraintViolationException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        p.setTitle("Invalid request parameter");
        return p;
    }
}
