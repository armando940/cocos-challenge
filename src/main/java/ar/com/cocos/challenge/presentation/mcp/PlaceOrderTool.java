package ar.com.cocos.challenge.presentation.mcp;

import ar.com.cocos.challenge.domain.model.Money;
import ar.com.cocos.challenge.domain.model.OrderSide;
import ar.com.cocos.challenge.domain.model.OrderStatus;
import ar.com.cocos.challenge.domain.model.OrderType;
import ar.com.cocos.challenge.domain.port.in.PlaceOrderPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * MCP Tool para enviar órdenes al mercado.
 * COMENTADO: Requiere spring-ai-tool dependency que no está en mavenCentral estable.
 */
// @Component
// @RequiredArgsConstructor
public class PlaceOrderTool {

    // private final PlaceOrderPort placeOrderPort;

    // @Tool(name = "place_order", description = "Envía una orden de compra o venta al mercado (MARKET o LIMIT)")
    // public OrderResult placeOrder(
    //         @ToolParam(description = "ID del usuario") Long userId,
    //         @ToolParam(description = "ID del instrumento") Long instrumentId,
    //         @ToolParam(description = "Lado de la orden: BUY, SELL, CASH_IN, CASH_OUT") String side,
    //         @ToolParam(description = "Tipo de orden: MARKET o LIMIT") String type,
    //         @ToolParam(description = "Precio por acción (obligatorio para LIMIT)") String price,
    //         @ToolParam(description = "Cantidad de acciones (XOR con amount)") Long size,
    //         @ToolParam(description = "Monto total en pesos (XOR con size)") String amount,
    //         @ToolParam(description = "Clave de idempotencia (UUID v4)") String idempotencyKey
    // ) {
    //     PlaceOrderPort.Command command = new PlaceOrderPort.Command(
    //             userId,
    //             instrumentId,
    //             OrderSide.valueOf(side.toUpperCase()),
    //             OrderType.valueOf(type.toUpperCase()),
    //             price != null && !price.isBlank() ? Money.of(new BigDecimal(price)) : null,
    //             size,
    //             amount != null && !amount.isBlank() ? Money.of(new BigDecimal(amount)) : null,
    //             idempotencyKey
    //     );
    //
    //     PlaceOrderPort.Result result = placeOrderPort.execute(command);
    //
    //     return new OrderResult(
    //             result.orderId(),
    //             result.status(),
    //             result.executedPrice() != null ? result.executedPrice().toString() : null,
    //             result.executedSize(),
    //             result.rejectionReason(),
    //             result.datetime()
    //     );
    // }

    // public record OrderResult(
    //         Long orderId,
    //         OrderStatus status,
    //         String executedPrice,
    //         Long executedSize,
    //         String rejectionReason,
    //         Instant datetime
    // ) {}
}