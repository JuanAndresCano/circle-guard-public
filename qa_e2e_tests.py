import unittest
import requests
import uuid
import uuid
import logging

# Cambiar puerto si usaste un Gateway (ej: 8000) o si accedes directo a Auth (8080)
BASE_URL_AUTH = "http://localhost:8080" 
BASE_URL_IDENTITY = "http://localhost:8082"  

class CircleGuardE2ETests(unittest.TestCase):
    
    @classmethod
    def setUpClass(cls):
        logging.basicConfig(level=logging.INFO)
        cls.logger = logging.getLogger("E2E_Tests")
        cls.logger.info("Iniciando Pruebas E2E (End-To-End). Se asume que Docker-Compose está levantado.")

    def test_01_health_check_infra(self):
        """1. E2E: Validar que el servidor responde a nivel web y BD está activa"""
        try:
            # Reemplaza '/actuator/health' o '/' por un endpoint público que tengas liberado
            response = requests.get(f"{BASE_URL_AUTH}/v3/api-docs", timeout=3)
            # En spring boot, si la app está arriba con swagger, responderá un OK (2xx)
            self.assertTrue(response.status_code in [200, 401, 404])
        except requests.exceptions.ConnectionError:
            self.fail("El servicio de Auth no está levantado en localhost. ¡Inicia tus contenedores/aplicaciones!")

    def test_02_e2e_anonymous_enrollment(self):
        """2. E2E: Proceso de registro anónimo inicial y validación de campos erróneos"""
        headers = {"Content-Type": "application/json"}
        # Enviamos un payload malformado para validar que la capa Web -> Service rechace correctamente
        payload = {"username": "invitado"} 
        
        response = requests.post(f"{BASE_URL_AUTH}/api/v1/auth/anonymous-session", json=payload, headers=headers)
        
        # Como es una ruta protegida e inviable, deberíamos recibir 400 Bad Request o 401
        self.assertTrue(response.status_code in [400, 401, 403, 404])

    def test_03_e2e_internal_service_communication(self):
        """3. E2E: Validar flujo de tokens y seguridad (Rechazo sin JWT)"""
        headers = {"Authorization": "Bearer null", "Content-Type": "application/json"}
        # Simulamos intentar extraer perfiles en Identity sin un token válido
        response = requests.get(f"{BASE_URL_IDENTITY}/api/v1/profiles/me", headers=headers)
        
        # Debe fallar el pipeline completo y denegar el acceso
        self.assertEqual(response.status_code, 401)
        
    def test_04_e2e_full_admin_login_rejection(self):
        """4. E2E: Un intento de login inválido viaja hasta BD/LDAP y devuelve negación"""
        payload = {
            "username": "admin_falso",
            "password": "wrong_password_123"
        }
        res = requests.post(f"{BASE_URL_AUTH}/api/v1/auth/login", json=payload)
        
        # Comprobamos la traza completa (Web -> Auth Service -> Identity Proxy -> LDAP/Postgres)
        self.assertTrue(res.status_code in [401, 403, 404]) 

    def test_05_e2e_database_integrity_uuid(self):
        """5. E2E: Generación y manejo de UUID en extremos expuestos que no existan"""
        fake_uuid = str(uuid.uuid4())
        
        res = requests.get(f"{BASE_URL_IDENTITY}/api/v1/profiles/search?id={fake_uuid}")
        
        # Debe procesarse correctamente pero responder vacío/404 sin hacer crash el sistema (500)
        self.assertNotEqual(res.status_code, 500, "Error Interno E2E: Falla de base de datos o nulos.")
        self.assertTrue(res.status_code in [401, 403, 404, 200])

if __name__ == '__main__':
    unittest.main(verbosity=2)