package br.com.contabilizapharma.relatorios.empresa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EstabelecimentoRepository extends JpaRepository<Estabelecimento, Integer> {
    List<Estabelecimento> findByEmpresaIdOrderByMatrizDescCnpjAsc(Integer empresaId);
    boolean existsByCnpj(String cnpj);
    boolean existsByCodigoDominio(Integer codigoDominio);
}
