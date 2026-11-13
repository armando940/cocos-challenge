package ar.com.cocos.challenge.domain.model;

/**
 * Estado de la orden en el sistema.
 */
public enum OrderStatus {
    NEW,        // Orden límite enviada, pendiente de ejecución
    FILLED,     // Orden ejecutada (MARKET inmediata, o LIMIT ejecutada)
    REJECTED,   // Rechazada por validación (saldo/tenencia insuficiente)
    CANCELLED   // Cancelada por el usuario (solo NEW)
}