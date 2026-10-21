package io.coffeeshop.order.web;

import io.coffeeshop.core.domain.OrderStateException;
import io.coffeeshop.order.service.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Единый формат ошибок (RFC 9457 Problem Details).
 *
 * <p>Нарушение жизненного цикла заказа — конфликт состояния (409), а не ошибка
 * запроса: запрос корректен, но заказ уже в терминальном статусе.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail onNotFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(OrderStateException.class)
    public ProblemDetail onIllegalTransition(OrderStateException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Illegal order state transition");
        problem.setProperty("orderId", e.orderId());
        problem.setProperty("current", e.current());
        problem.setProperty("target", e.target());
        return problem;
    }
}
