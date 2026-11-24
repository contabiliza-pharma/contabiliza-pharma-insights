package br.com.contabilizapharma.relatorios.usuario;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByPerfilAndAtivoTrue(Perfil perfil);

    /** Incremento atômico no banco (Guia §6.15); devolve o novo valor. */
    @Query(value = """
            UPDATE usuario SET tentativas_falhas = tentativas_falhas + 1
             WHERE id = :id RETURNING tentativas_falhas
            """, nativeQuery = true)
    short incrementarTentativas(@Param("id") Integer id);

    @Modifying
    @Query(value = "UPDATE usuario SET bloqueado_ate = :ate, tentativas_falhas = 0 WHERE id = :id",
            nativeQuery = true)
    void bloquearAte(@Param("id") Integer id, @Param("ate") LocalDateTime ate);
}
