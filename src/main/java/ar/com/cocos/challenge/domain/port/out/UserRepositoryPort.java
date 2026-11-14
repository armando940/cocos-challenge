package ar.com.cocos.challenge.domain.port.out;

import java.util.Optional;

/**
 * Puerto de salida para el repositorio de usuarios.
 *
 * <p>Expone un mutex por cuenta: {@link #lockById(Long)} adquiere un lock pesimista
 * ({@code SELECT ... FOR UPDATE}) sobre la fila del usuario dentro de la transacción corriente.</p>
 */
public interface UserRepositoryPort {

    /**
     * Adquiere un lock pesimista ({@code SELECT ... FOR UPDATE}) sobre la fila del usuario
     * dentro de la transacción corriente.
     *
     * <p>El lock se mantiene hasta el commit de la transacción. Si el usuario no existe,
     * el lock no encuentra fila y se comporta como no-op.</p>
     *
     * @param userId identificador del usuario a lockear
     * @return el id del usuario si existe, {@link Optional#empty()} si no existe
     */
    Optional<Long> lockById(Long userId);
}
