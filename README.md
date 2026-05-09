# CircleGuard Monorepo

**Absolute Privacy. High-Speed Containment. Secure Campus.**

CircleGuard is a state-of-the-art university contact tracing and fencing system designed to identify interconnected contact groups ("Circles") and apply rapid health fences while preserving individual anonymity.

---

## Vision & Mission

Our vision is a university campus where health containment speed outpaces lab confirmation timelines without compromising student privacy. CircleGuard leverages campus-native intelligenceâ€”class schedules and WiFi infrastructure to deliver a human-validated, graph-based protection ecosystem.

### Key Differentiators
- **Privacy-as-Code**: Zero real-name exposure outside a secure Health Center vault.
- **Recursive Containment**: Status promotion cascades that trigger in milliseconds.
- **Campus Integration**: Smart check-ins using existing WiFi AP triangulation and Bluetooth Low Energy (BLE).

---

## 📊 Success Metrics

| Metric | Target | Measurement |
|:---|:---|:---|
| **Containment Speed** | < 60 Seconds | Automated test of promotion engine cascade |
| **Privacy Compliance** | 100% Anonymity | Penetration test on graph database (Zero real names) |
| **Check-in Adoption** | > 70% | Analytics on scheduled class contact validation |
| **False Positive Rate** | < 15% | Post-fence surveys of actual vs. suspected contact |
| **System Uptime** | 99.5% | 7:00 AM - 10:00 PM (Academic Peak Hours) |

---

## 🏗️ Architecture Overview

CircleGuard follows a **Microservice Architecture** built on a **Hybrid Data Model**.

### Core Engine
1. **Status Promotion Machine**: Uses **Neo4j** for recursive graph traversals to identify contacts within a 14-day temporal window.
2. **Anonymization Vault**: A segregated **PostgreSQL** vault handles salted-hash identity mapping, compliant with **FERPA** regulations.
3. **Event-Driven Core**: **Apache Kafka** manages asynchronous status changes, audit logs, and notification dispatches.

### Services Directory
- **Auth Service**: Dual-chain LDAP (University) / Local (Guest) auth with Dynamic RBAC.
- **Identity Service**: Cryptographic vault for anonymizing real identities.
- **Promotion Service**: The status engine (Recursive Graph Processing).
- **Notification Service**: Multi-channel dispatcher (Push/Email/SMS).
- **Form Service**: Dynamic health questionnaire engine.
- **Gateway Service**: Campus entry validation via signed, time-limited QR tokens.
- **Dashboard Service**: Geospatial hotspot analytics (Privacy-preserving).
- **File Service**: Secure certificate and document storage (S3-compatible).

---

## 🛠️ Technical Stack

| Layer | Technology | Rationale |
|:---|:---|:---|
| **Backend** | Spring Boot 4 / Java 21 | Enterprise-grade maturity & low-latency Jakarta EE support. |
| **Graph DB** | Neo4j 5.26 | High-performance recursive traversals unreachable with SQL. |
| **Relational DB**| PostgreSQL 16 | ACID compliant storage for identity and configuration. |
| **Message Bus** | Apache Kafka 7.6 | Persistent, audit-trailed event log for status dispatches. |
| **Caching** | Redis 7.2 | L2 distributed cache for rapid entry-gate status validation. |
| **Mobile/Web** | Expo (React Native) | Unified codebase across iOS, Android, and Browser. |
| **Infra** | Kubernetes | Orchestration for high availability and auto-scaling. |

---

## 🗺️ Roadmap

### Phase 1: MVP — The Intelligence Core (Current)
- [x] Status Promotion Machine (Suspect → Probable → Confirmed).
- [x] Temporal graph with 14-day TTL edges.
- [x] Multi-channel fence notifications (Push/Email/SMS).
- [ ] Health Center de-identification console.

### Phase 2: Growth — Spatial Intelligence
- [ ] WiFi AP triangulation integration.
- [ ] Campus entry validation (Gatekeeper) QR integration.
- [ ] LMS integration for "Remote Attendance" status automation.

### Phase 3: Vision — Full Ecosystem
- [ ] Off-campus circle detection via P2P Bluetooth.
- [ ] Global Health Dashboard with hotspot visualization.
- [ ] Lab API bridge for automated test result ingestion.

---

## 🚀 Taller 2: Pruebas y Lanzamiento (Entregables)

Durante este hito, estabilizamos el sistema para su lanzamiento ejecutando los siguientes pilares de pruebas y automatización:

1. **Pruebas Unitarias e Integración (100% Pass):**
   - Validamos exitosamente de forma aislada todos los microservicios (`auth`, `identity`, `dashboard`, `gateway`, `form`, `notification`, `file`, `promotion`).
   - Se corrigieron las políticas de seguridad (RBAC) en el `promotion-service` ajustando las anotaciones asociadas a `@WithMockUser`.
2. **Pruebas de Carga (Locust):**
   - Se creó un escenario en Python con `locustfile.py` para inyectar tráfico masivo y validar cuellos de botella (Ej: simulando 1000 usuarios concurrentes contra el sistema).
3. **Pruebas End-to-End (E2E):**
   - Validación completa del ciclo de vida del usuario, garantizando la orquestación en cadena de los componentes en conjunto.
4. **CI/CD y Despliegue en Kubernetes (Helm):**
   - Se construyó el **Jenkinsfile** para la integración y ejecución continua en pipeline.
   - Se empaquetó toda la infraestructura en **Helm Charts** para un despliegue declarativo y escalable.

---

## 💻 Local Development

### 1. Infrastructure
Ensure Docker is installed, then start the middleware stack:
```bash
docker-compose -f docker-compose.dev.yml up -d
```
*Middleware includes: PostgreSQL, Neo4j, Kafka, Zookeeper, Redis, and OpenLDAP.*

### 2. Build & Run
CircleGuard uses Gradle for parallel builds across services:
```bash
# Start all microservices in parallel
./gradlew bootRun --parallel

# Start a specific service
./gradlew :services:<service-name>:bootRun
```

### 3. API Exploration
Every service exposes an OpenAPI 3.0 interface. Once running, visit:
`http://localhost:<service-port>/swagger-ui/index.html`

---

## 📱 Frontend Development

The frontend is built using **Expo (React Native)**, supporting iOS, Android, and Web from a single codebase located in `/mobile`.

### 1. Prerequisites
Ensure you have Node.js installed and dependencies loaded:
```bash
cd mobile
npm install
```

### 2. Run the Application
You can run the app in various modes depending on your target platform:

| Platform | Command | Notes |
|:---|:---|:---|
| **Development Menu** | `npm run start` | Opens the Expo Go start-up menu. |
| **Android** | `npm run android` | Requires Android Studio / Emulator or a connected device. |
| **iOS** | `npm run ios` | Requires macOS with Xcode / Simulator installed. |
| **Web Browser** | `npm run web` | Launches the dashboard/app in your default browser. |

### 3. Testing
To run frontend unit and component tests:
```bash
npm run test
```

---

## 🧪 Testing

We maintain high system integrity via multi-level testing:

### 💡 Optimización de Recursos ("Cómo probar sin morir en el intento")
La arquitectura de microservicios en Java puede consumir rápidamente toda la memoria de tu entorno local. Para evitar bloqueos (*Broken pipe*, *OOM*, o puertos ocupados indefinidamente), sigue estas reglas de oro:

1. **Limitar Memoria y Desactivar Daemons:**
   Al lanzar pruebas, ejecuta *siempre* Gradle restringiendo el montón de memoria para la JVM de la tarea, e indica explícitamente que no se monte como proceso "demonio":
   ```bash
   ./gradlew test -D"org.gradle.jvmargs=-Xmx256m" --no-daemon
   ```

2. **Matar Procesos Zombis (Si Windows falla en limpiar directorios):**
   Si Gradle falla quejándose en un pipeline con un error tipo `Unable to delete directory` o puertos bajo uso sin razón aparente, tienes procesos de Java huérfanos. Límpialos en CMD/PowerShell:
   ```bash
   taskkill /F /IM java.exe
   ```

---

### Command Cheat Sheet

#### Ejecutar Tests (Servicio por Servicio)

| Command | Scope |
|:---|:---|
| `./gradlew test -D"org.gradle.jvmargs=-Xmx256m" --no-daemon` | Full system suite (Unit + Integration) |
| `./gradlew :services:circleguard-auth-service:test -D"org.gradle.jvmargs=-Xmx256m" --no-daemon` | Auth Service testing |
| `./gradlew :services:circleguard-identity-service:test -D"org.gradle.jvmargs=-Xmx256m" --no-daemon` | Identity Service testing |
| `./gradlew :services:circleguard-promotion-service:test -D"org.gradle.jvmargs=-Xmx256m" --no-daemon` | Promotion Service testing |
| `./gradlew :services:circleguard-notification-service:test -D"org.gradle.jvmargs=-Xmx256m" --no-daemon` | Notification Service testing |
| `./gradlew :services:circleguard-form-service:test -D"org.gradle.jvmargs=-Xmx256m" --no-daemon` | Form Service testing |
| `./gradlew :services:circleguard-gateway-service:test -D"org.gradle.jvmargs=-Xmx256m" --no-daemon` | Gateway Service testing |
| `./gradlew :services:circleguard-dashboard-service:test -D"org.gradle.jvmargs=-Xmx256m" --no-daemon` | Dashboard Service testing |
| `./gradlew :services:circleguard-file-service:test -D"org.gradle.jvmargs=-Xmx256m" --no-daemon` | File Service testing |

#### Ejecutar la Aplicación (Servicio por Servicio)

| Command | Scope |
|:---|:---|
| `./gradlew bootRun --parallel` | Run all microservices |
| `./gradlew :services:circleguard-auth-service:bootRun` | Run Auth Service |
| `./gradlew :services:circleguard-identity-service:bootRun` | Run Identity Service |
| `./gradlew :services:circleguard-promotion-service:bootRun` | Run Promotion Service |
| `./gradlew :services:circleguard-notification-service:bootRun` | Run Notification Service |
| `./gradlew :services:circleguard-form-service:bootRun` | Run Form Service |
| `./gradlew :services:circleguard-gateway-service:bootRun` | Run Gateway Service |
| `./gradlew :services:circleguard-dashboard-service:bootRun` | Run Dashboard Service |
| `./gradlew :services:circleguard-file-service:bootRun` | Run File Service |

**Note**: Integration tests use **Testcontainers** to spawn ephemeral Neo4j and PostgreSQL instances for zero-side-effect validation.

---

## 🔐 Privacy & Compliance

- **FERPA Compliance**: Student identities are never stored in the contact graph.
- **Right to be Forgotten**: Users can trigger complete data purging via the Identity Vault.
- **Temporal Privacy**: All contact edges are automatically purged after 14 days.
