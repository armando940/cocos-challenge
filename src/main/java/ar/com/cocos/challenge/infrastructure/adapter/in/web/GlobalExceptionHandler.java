package ar.com.cocos.challenge.infrastructure.adapter.in.web;

import ar.com.cocos.challenge.domain.model.IdempotencyConflictException;
import ar.com.cocos.challenge.domain.model.InstrumentNotFoundException;
import ar.com.cocos.challenge.domain.model.InstrumentPriceNotFoundException;
import ar.com.cocos.challenge.domain.model.InsufficientCashException;
import ar.com.cocos.challenge.domain.model.InsufficientSharesException;
import ar.com.cocos.challenge.domain.model.OrderCannotBeCancelledException;
import ar.com.cocos.challenge.domain.model.OrderNotFoundException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Manejador global de excepciones - RFC 7807 ProblemDetail.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InsufficientCashException.class)
    public ResponseEntity<ProblemDetail> handleInsufficientCash(InsufficientCashException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.builder()
                .type(URI.create("https://cocos.challenge/errors/insufficient-cash"))
                .title("Fondos insuficientes")
                .status(HttpStatus.BAD_REQUEST.value())
                .detail(ex.getMessage())
                .instance(URI.create(request.getDescription(false)))
                .property("available", ex.getAvailable().toString())
                .property("required", ex.getRequired().toString())
                .property("traceId", MDC.get("traceId"))
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(InsufficientSharesException.class)
    public ResponseEntity<ProblemDetail> handleInsufficientShares(InsufficientSharesException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.builder()
                .type(URI.create("https://cocos.challenge/errors/insufficient-shares"))
                .title("Acciones insuficientes")
                .status(HttpStatus.BAD_REQUEST.value())
                .detail(ex.getMessage())
                .instance(URI.create(request.getDescription(false)))
                .property("available", ex.getAvailable())
                .property("required", ex.getRequired())
                .property("traceId", MDC.get("traceId"))
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(InstrumentNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleInstrumentNotFound(InstrumentNotFoundException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.builder()
                .type(URI.create("https://cocos.challenge/errors/instrument-not-found"))
                .title("Instrumento no encontrado")
                .status(HttpStatus.NOT_FOUND.value())
                .detail(ex.getMessage())
                .instance(URI.create(request.getDescription(false)))
                .property("instrumentId", ex.getInstrumentId())
                .property("traceId", MDC.get("traceId"))
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(InstrumentPriceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handlePriceNotFound(InstrumentPriceNotFoundException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.builder()
                .type(URI.create("https://cocos.challenge/errors/price-not-found"))
                .title("Precio no disponible")
                .status(HttpStatus.NOT_FOUND.value())
                .detail(ex.getMessage())
                .instance(URI.create(request.getDescription(false)))
                .property("instrumentId", ex.getInstrumentId())
                .property("traceId", MDC.get("traceId"))
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleOrderNotFound(OrderNotFoundException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.builder()
                .type(URI.create("https://cocos.challenge/errors/order-not-found"))
                .title("Orden no encontrada")
                .status(HttpStatus.NOT_FOUND.value())
                .detail(ex.getMessage())
                .instance(URI.create(request.getDescription(false)))
                .property("orderId", ex.getOrderId())
                .property("traceId", MDC.get("traceId"))
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(OrderCannotBeCancelledException.class)
    public ResponseEntity<ProblemDetail> handleCannotCancel(OrderCannotBeCancelledException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.builder()
                .type(URI.create("https://cocos.challenge/errors/cannot-cancel"))
                .title("Orden no puede cancelarse")
                .status(HttpStatus.CONFLICT.value())
                .detail(ex.getMessage())
                .instance(URI.create(request.getDescription(false)))
                .property("orderId", ex.getOrderId())
                .property("currentStatus", ex.getCurrentStatus().name())
                .property("traceId", MDC.get("traceId"))
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ProblemDetail> handleIdempotencyConflict(IdempotencyConflictException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.builder()
                .type(URI.create("https://cocos.challenge/errors/idempotency-conflict"))
                .title("Conflicto de idempotencia")
                .status(HttpStatus.CONFLICT.value())
                .detail(ex.getMessage())
                .instance(URI.create(request.getDescription(false)))
                .property("key", ex.getKey())
                .property("traceId", MDC.get("traceId"))
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.builder()
                .type(URI.create("https://cocos.challenge/errors/bad-request"))
                .title("Solicitud inválida")
                .status(HttpStatus.BAD_REQUEST.value())
                .detail(ex.getMessage())
                .instance(URI.create(request.getDescription(false)))
                .property("traceId", MDC.get("traceId"))
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = error instanceof FieldError ? ((FieldError) error).getField() : error.getObjectName();
            errors.put(field, error.getDefaultMessage());
        });

        ProblemDetail problem = ProblemDetail.builder()
                .type(URI.create("https://cocos.challenge/errors/validation"))
                .title("Error de validación")
                .status(HttpStatus.BAD_REQUEST.value())
                .detail("Uno o más campos son inválidos")
                .instance(URI.create(request.getDescription(false)))
                .property("errors", errors)
                .property("traceId", MDC.get("traceId"))
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.builder()
                .type(URI.create("https://cocos.challenge/errors/internal"))
                .title("Error interno del servidor")
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .detail("Ha ocurrido un error inesperado")
                .instance(URI.create(request.getDescription(false)))
                .property("traceId", MDC.get("traceId"))
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    // Builder para ProblemDetail (RFC 7807)
    public static class ProblemDetail {
        private URI type;
        private String title;
        private int status;
        private String detail;
        private URI instance;
        private final Map<String, Object> properties = new HashMap<>();

        private ProblemDetail() {}

        public static Builder builder() {
            return new Builder();
        }

        public URI getType() { return type; }
        public String getTitle() { return title; }
        public int getStatus() { return status; }
        public String getDetail() { return detail; }
        public URI getInstance() { return instance; }
        public Map<String, Object> getProperties() { return properties; }

        public static class Builder {
            private final ProblemDetail pd = new ProblemDetail();

            public Builder type(URI type) { pd.type = type; return this; }
            public Builder title(String title) { pd.title = title; return this; }
            public Builder status(int status) { pd.status = status; return this; }
            public Builder detail(String detail) { pd.detail = detail; return this; }
            public Builder instance(URI instance) { pd.instance = instance; return this; }
            public Builder property(String key, Object value) { pd.properties.put(key, value); return this; }
            public ProblemDetail build() { return pd; }
        }
    }
}