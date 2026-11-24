#!/bin/sh
# Gera o hash Argon2id da senha do primeiro administrador (variável ADMIN_SENHA_HASH).
# Só precisa do Docker. A senha é digitada sem aparecer na tela e não fica gravada em lugar nenhum.
# Parâmetros iguais aos da API Java (SenhaConfig.java): m=65536, t=3, p=4.
exec docker run --rm -it python:3.12-slim sh -c "pip install -q --disable-pip-version-check argon2-cffi 2>/dev/null && python -c 'import getpass;from argon2 import PasswordHasher as H;print(H(time_cost=3,memory_cost=65536,parallelism=4).hash(getpass.getpass()))'"
