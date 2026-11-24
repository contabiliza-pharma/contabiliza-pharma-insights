package br.com.contabilizapharma.relatorios.seed;

import br.com.contabilizapharma.relatorios.config.AppProperties;
import br.com.contabilizapharma.relatorios.core.auditoria.Auditoria;
import br.com.contabilizapharma.relatorios.usuario.Perfil;
import br.com.contabilizapharma.relatorios.usuario.Usuario;
import br.com.contabilizapharma.relatorios.usuario.UsuarioRepository;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeder do primeiro administrador. Roda a cada subida, depois das migrações, mas só cria
 * a conta se o e-mail ainda não existir — nunca sobrescreve uma conta (nem a senha) já criada.
 *
 * Variáveis: ADMIN_NOME, ADMIN_EMAIL, ADMIN_SENHA_HASH (Argon2id gerado pelo comando do README).
 * A senha em texto puro nunca passa pela aplicação nem fica no .env.
 */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);
    private static final Pattern HASH_ARGON2ID =
            Pattern.compile("^\\$argon2id\\$v=19\\$m=\\d+,t=\\d+,p=\\d+\\$[A-Za-z0-9+/]+\\$[A-Za-z0-9+/]+$");

    private final UsuarioRepository usuarios;
    private final AppProperties.Admin admin;
    private final Auditoria auditoria;

    public AdminSeeder(UsuarioRepository usuarios, AppProperties props, Auditoria auditoria) {
        this.usuarios = usuarios;
        this.admin = props.admin();
        this.auditoria = auditoria;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (vazio(admin.email()) || vazio(admin.senhaHash()) || "''".equals(admin.senhaHash().trim())) {
            if (usuarios.countByPerfilAndAtivoTrue(Perfil.administrador) == 0) {
                log.warn("seed_admin_ignorado: defina ADMIN_EMAIL e ADMIN_SENHA_HASH para criar o primeiro administrador");
            }
            return;
        }
        String email = admin.email().trim().toLowerCase(Locale.ROOT);
        if (usuarios.existsByEmail(email)) {
            log.info("seed_admin: administrador {} já existe; nada a fazer", Auditoria.mascararEmail(email));
            return;
        }
        // No .env o hash fica entre aspas simples (por causa do Docker Compose); lido como .properties, elas vêm junto
        String hash = admin.senhaHash().trim().replaceAll("^'(.*)'$", "$1");
        if (!HASH_ARGON2ID.matcher(hash).matches()) {
            throw new IllegalStateException("ADMIN_SENHA_HASH não é um hash Argon2id válido. "
                    + "Gere com o comando do README e coloque entre aspas simples no .env.");
        }
        String nome = vazio(admin.nome()) ? "Administrador" : admin.nome().trim();
        Usuario u = new Usuario(nome, email, hash, Perfil.administrador);
        u.definirSenhaDefinitiva(hash);   // a senha foi escolhida por quem gerou o hash
        u = usuarios.save(u);
        auditoria.registrar(u.getId(), "seed_admin", "usuario:" + u.getId(), "local");
        log.info("seed_admin: administrador {} criado", Auditoria.mascararEmail(email));
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }
}
