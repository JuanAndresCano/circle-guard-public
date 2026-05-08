from locust import HttpUser, task, between

class CircleGuardStressUser(HttpUser):
    # Simulamos un usuario que piensa o navega entre 1 y 3 segundos entre cada petición
    wait_time = between(1, 3)

    # Nota: Si tu servicio corre en otro puerto al hacer las pruebas, se especifica al ejecutar locust

    @task(3)
    def test_anonymous_enrollment_stress(self):
        """Simula a miles de usuarios intentando registrarse simultáneamente sin credenciales completas"""
        # Enviamos un payload básico para ver cómo el servidor rechaza peticiones masivas
        self.client.post("/api/v1/auth/anonymous-session", json={"username": "load_tester"}, name="Anonymous Enrollment Form")

    @task(1)
    def test_health_check_stress(self):
        """Simula tráfico a un endpoint público para evaluar si el servidor general cae"""
        # Llama a un endpoint de salud/documentación
        self.client.get("/v3/api-docs", name="Get API Docs (Health Check)")

    @task(2)
    def test_failed_login_stress(self):
        """Simula la autenticación hacia la DB/LDAP para estresar la conexión real a Postgres"""
        payload = {
            "username": "stress_admin",
            "password": "wrong_password"
        }
        self.client.post("/api/v1/auth/login", json=payload, name="Full Login Endpoint")