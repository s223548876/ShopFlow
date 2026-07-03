package com.shopflow.common.error;

import com.shopflow.auth.EmailAlreadyExistsException;
import com.shopflow.auth.InvalidCredentialsException;
import com.shopflow.cart.CartItemAlreadyExistsException;
import com.shopflow.cart.CartItemNotFoundException;
import com.shopflow.cart.InsufficientStockException;
import com.shopflow.cart.ProductUnavailableException;
import com.shopflow.catalog.CategoryNotFoundException;
import com.shopflow.catalog.InvalidPageRequestException;
import com.shopflow.catalog.InvalidSortException;
import com.shopflow.catalog.ProductNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<FieldErrorResponse> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(error -> new FieldErrorResponse(error.getField(), error.getDefaultMessage()))
                .toList();
        return response(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Request validation failed",
                request,
                fieldErrors
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleMalformedRequest(HttpServletRequest request) {
        return response(
                HttpStatus.BAD_REQUEST,
                "MALFORMED_REQUEST",
                "Request body is malformed or contains unsupported fields",
                request,
                List.of()
        );
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    ResponseEntity<ApiErrorResponse> handleDuplicateEmail(HttpServletRequest request) {
        return response(
                HttpStatus.CONFLICT,
                "EMAIL_ALREADY_EXISTS",
                "Email is already registered",
                request,
                List.of()
        );
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidCredentials(HttpServletRequest request) {
        return response(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "Invalid email or password",
                request,
                List.of()
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        if ("page".equals(exception.getName()) || "size".equals(exception.getName())) {
            return handleInvalidPage(request);
        }
        return response(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Request validation failed",
                request,
                List.of(new FieldErrorResponse(exception.getName(), "has an invalid value"))
        );
    }

    @ExceptionHandler(ProductNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleProductNotFound(HttpServletRequest request) {
        return response(
                HttpStatus.NOT_FOUND,
                "PRODUCT_NOT_FOUND",
                "Product not found",
                request,
                List.of()
        );
    }

    @ExceptionHandler(CartItemNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleCartItemNotFound(HttpServletRequest request) {
        return response(
                HttpStatus.NOT_FOUND,
                "CART_ITEM_NOT_FOUND",
                "Cart item not found",
                request,
                List.of()
        );
    }

    @ExceptionHandler(CartItemAlreadyExistsException.class)
    ResponseEntity<ApiErrorResponse> handleCartItemAlreadyExists(HttpServletRequest request) {
        return response(
                HttpStatus.CONFLICT,
                "CART_ITEM_ALREADY_EXISTS",
                "Product is already in the cart",
                request,
                List.of()
        );
    }

    @ExceptionHandler(ProductUnavailableException.class)
    ResponseEntity<ApiErrorResponse> handleProductUnavailable(HttpServletRequest request) {
        return response(
                HttpStatus.CONFLICT,
                "PRODUCT_UNAVAILABLE",
                "Product is unavailable",
                request,
                List.of()
        );
    }

    @ExceptionHandler(InsufficientStockException.class)
    ResponseEntity<ApiErrorResponse> handleInsufficientStock(HttpServletRequest request) {
        return response(
                HttpStatus.CONFLICT,
                "INSUFFICIENT_STOCK",
                "Insufficient stock",
                request,
                List.of()
        );
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleCategoryNotFound(HttpServletRequest request) {
        return response(
                HttpStatus.NOT_FOUND,
                "CATEGORY_NOT_FOUND",
                "Category not found",
                request,
                List.of()
        );
    }

    @ExceptionHandler(InvalidPageRequestException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidPage(HttpServletRequest request) {
        return response(
                HttpStatus.BAD_REQUEST,
                "INVALID_PAGE_REQUEST",
                "Page must be non-negative and size must be between 1 and 100",
                request,
                List.of()
        );
    }

    @ExceptionHandler(InvalidSortException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidSort(HttpServletRequest request) {
        return response(
                HttpStatus.BAD_REQUEST,
                "INVALID_SORT",
                "Sort must use name, price, or createdAt with asc or desc direction",
                request,
                List.of()
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> handleAccessDenied(HttpServletRequest request) {
        return response(
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                "You do not have permission to access this resource",
                request,
                List.of()
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiErrorResponse> handleNotFound(HttpServletRequest request) {
        return response(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "Resource not found",
                request,
                List.of()
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(HttpServletRequest request) {
        return response(
                HttpStatus.METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED",
                "Request method is not supported",
                request,
                List.of()
        );
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(HttpServletRequest request) {
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                request,
                List.of()
        );
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            List<FieldErrorResponse> fieldErrors
    ) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                Instant.now(),
                status.value(),
                code,
                message,
                request.getRequestURI(),
                fieldErrors
        ));
    }
}
