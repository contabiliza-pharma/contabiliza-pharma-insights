package br.com.contabilizapharma.relatorios.empresa;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmpresaRepository extends JpaRepository<Empresa, Integer> {
    Optional<Empresa> findByRaizCnpj(String raizCnpj);
}
