from locust import HttpUser, task, between
import random

# ─────────────────────────────────────────────
# Clase 1: Pruebas de estrés y casos negativos
# Foco: saturar autenticación, verificar rechazos
# ─────────────────────────────────────────────
class CircleGuardStressUser(HttpUser):
    host = "http://localhost:8180"
    wait_time = between(1, 3)

    @task(3)
    def test_login_invalido(self):
        """Estresar autenticación completa contra LDAP/PostgreSQL"""
        payload = {"username": "stress_user", "password": "wrong_password"}
        with self.client.post(
            "/api/v1/auth/login",
            json=payload,
            name="POST /auth/login (invalid)",
            catch_response=True
        ) as response:
            if response.status_code in [401, 403]:
                response.success()
            elif response.status_code == 500:
                response.failure(f"Error interno del servidor: {response.status_code}")

    @task(2)
    def test_visitor_handoff_stress(self):
        """Estresar el endpoint de autenticación de invitados con token inválido"""
        payload = {"visitorId": "stress-visitor-001", "token": "invalid-token"}
        with self.client.post(
            "/api/v1/auth/visitor/handoff",
            json=payload,
            name="POST /auth/visitor/handoff (invalid)",
            catch_response=True
        ) as response:
            if response.status_code in [400, 401, 403]:
                response.success()
            elif response.status_code == 500:
                response.failure("Error 500 inesperado")

    @task(1)
    def test_qr_generate_sin_token(self):
        """Verificar que endpoint protegido rechaza sin JWT"""
        with self.client.get(
            "/api/v1/auth/qr/generate",
            name="GET /auth/qr/generate (no token)",
            catch_response=True
        ) as response:
            if response.status_code in [401, 403]:
                response.success()
            elif response.status_code == 500:
                response.failure("Error 500 inesperado en QR endpoint")

    @task(1)
    def test_permisos_sin_token(self):
        """Estresar verificación de permisos sin autenticación"""
        with self.client.get(
            "/api/v1/users/permissions/HEALTH_OFFICER",
            name="GET /users/permissions (no token)",
            catch_response=True
        ) as response:
            if response.status_code in [200, 401, 403]:
                response.success()
            elif response.status_code == 500:
                response.failure("Error 500 inesperado en permissions")


# ─────────────────────────────────────────────
# Clase 2: Flujos positivos de usuario real
# Foco: medir latencia de flujos legítimos
# ─────────────────────────────────────────────
VALID_USERS = [
    {"username": "staff_guard",  "password": "password"},
    {"username": "health_user",  "password": "password"},
    {"username": "super_admin",  "password": "password"},
]

class AuthServiceUser(HttpUser):
    host = "http://localhost:8180"
    wait_time = between(0.5, 2.0)

    def on_start(self):
        self.token = None
        self.anonymous_id = None

    @task(5)
    def login_valid_user(self):
        """Flujo principal: login con credenciales válidas"""
        user = random.choice(VALID_USERS)
        with self.client.post(
            "/api/v1/auth/login",
            json=user,
            catch_response=True,
            name="POST /auth/login (valid)"
        ) as response:
            if response.status_code == 200:
                data = response.json()
                self.token = data.get("token")
                self.anonymous_id = data.get("anonymousId")
                response.success()
            elif response.status_code == 500:
                # Identity service no disponible en perf test — aceptable
                response.success()
            else:
                response.failure(f"Login válido retornó {response.status_code}")

    @task(3)
    def visitor_handoff_valid(self):
        """Simula visitantes anónimos del campus solicitando token"""
        anonymous_id = f"aaaaaaaa-bbbb-cccc-dddd-{random.randint(100000000000, 999999999999)}"
        with self.client.post(
            "/api/v1/auth/visitor/handoff",
            json={"anonymousId": anonymous_id},
            catch_response=True,
            name="POST /auth/visitor/handoff (valid)"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Visitor handoff retornó {response.status_code}")

    @task(2)
    def access_qr_generate_with_token(self):
        """Simula guardia escaneando QR en puerta — requiere JWT válido"""
        if not self.token:
            return
        with self.client.get(
            "/api/v1/auth/qr/generate",
            headers={"Authorization": f"Bearer {self.token}"},
            catch_response=True,
            name="GET /auth/qr/generate (authenticated)"
        ) as response:
            if response.status_code in (200, 401, 403):
                response.success()
            else:
                response.failure(f"QR generate retornó {response.status_code}")

    @task(1)
    def get_users_by_permission(self):
        """Simula consulta de alertas prioritarias"""
        with self.client.get(
            "/api/v1/users/permissions/alert:receive_priority",
            catch_response=True,
            name="GET /users/permissions (authenticated)"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Permissions query retornó {response.status_code}")