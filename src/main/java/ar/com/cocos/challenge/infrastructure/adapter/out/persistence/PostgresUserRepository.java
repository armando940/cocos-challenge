package ar.com.cocos.challenge.infrastructure.adapter.out.persistence;

import ar.com.cocos.challenge.domain.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Adaptador JPA para {@link UserRepositoryPort}.
 *
 * <p>Implementa el mutex por usuario mediante un lock pesimista
 * ({@code SELECT ... FOR UPDATE}) sobre la fila de {@code users.id}.</p>
 */
@Repository
@RequiredArgsConstructor
public class PostgresUserRepository implements UserRepositoryPort {

    private final UserJpaRepository jpaRepository;

    @Override
    public Optional<Long> lockById(Long userId) {
        return jpaRepository.findByIdWithLock(userId).map(UserEntity::getId);
    }
}
