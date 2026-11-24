# Windows (PowerShell). Gera o hash Argon2id da senha do primeiro administrador (ADMIN_SENHA_HASH).
# A senha é digitada sem aparecer na tela e não fica gravada. Parâmetros iguais aos da API Java.
docker run --rm -it python:3.12-slim sh -c "pip install -q --disable-pip-version-check argon2-cffi 2>/dev/null && python -c 'import getpass;from argon2 import PasswordHasher as H;print(H(time_cost=3,memory_cost=65536,parallelism=4).hash(getpass.getpass()))'"
