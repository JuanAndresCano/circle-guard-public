from locust import HttpUser, task, between
import json

class CircleGuardStressUser(HttpUser):
    # Auth service corre en 8180
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
            # 401 es el comportamiento esperado — no lo marques como falla
            if response.status_code in [401, 403]:
                response.success()
            elif response.status_code == 500:
                response.failure(f"Error interno del servidor: {response.status_code}")

    @task(2)
    def test_visitor_handoff_stress(self):
        """Estresar el endpoint de autenticación de invitados"""
        payload = {"visitorId": "stress-visitor-001", "token": "invalid-token"}
        with self.client.post(
            "/api/v1/auth/visitor/handoff",
            json=payload,
            name="POST /auth/visitor/handoff",
            catch_response=True
        ) as response:
            if response.status_code in [400, 401, 403]:
                response.success()
            elif response.status_code == 500:
                response.failure(f"Error 500 inesperado")

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
            if response.status_code in [401, 403]:
                response.success()
            elif response.status_code == 500:
                response.failure("Error 500 inesperado en permissions")