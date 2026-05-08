pipeline {
    // Ejecutar en cualquier agente de Jenkins disponible
    agent any

    environment {
        // Configuraciones globales del pipeline
        DOCKER_REGISTRY = 'registry.local:5000' // Ajustar si se usa DockerHub o un registry local
        APP_VERSION = "1.0.${BUILD_NUMBER}"
        
        // ¡Crucial! Evita los crashes de "Broken pipe" de tu máquina Windows en el pipeline
        GRADLE_USER_HOME = "C:\\gradle_cache"
    }

    stages {
        stage('Checkout') {
            steps {
                echo 'Obteniendo código fuente...'
                checkout scm
            }
        }

        stage('Build Compile') {
            steps {
                echo 'Compilando microservicios omitiendo pruebas...'
                // Utilizamos 'bat' porque Jenkins correrá sobre tu Windows local
                bat './gradlew build -x test --no-daemon'
            }
        }

        stage('Automated Tests') {
            steps {
                echo 'Ejecutando Pruebas Unitarias e Integración (Punto 3.a y 3.b)...'
                bat './gradlew test --no-daemon'
            }
            post {
                always {
                    // Recopila los resultados HTML/XML de JUnit para mostrarlos en la interfaz de Jenkins
                    junit '**/build/test-results/test/TEST-*.xml'
                }
            }
        }

        stage('Docker Packaging') {
            steps {
                echo 'Construyendo imágenes Docker de los servicios...'
                bat 'docker-compose -f docker-compose.dev.yml build'
                // Si tienes un registry configurado, aquí iría el 'docker push'
            }
        }

        stage('Deploy to Kubernetes (Helm)') {
            steps {
                echo 'Desplegando infraestructura en Minikube o K8s local usando el chart base (Punto 4)...'
                // Ejemplo para desplegar los ms principales reutilizando nuestro helm chart genérico
                bat '''
                helm upgrade --install auth-service ./helm/circleguard-microservice --set image.repository=circleguard/auth-service --set image.tag=latest --namespace circleguard --create-namespace
                helm upgrade --install identity-service ./helm/circleguard-microservice --set image.repository=circleguard/identity-service --set image.tag=latest --namespace circleguard
                '''
            }
        }

        stage('E2E & Performance Tests') {
            steps {
                echo 'Ejecutando pruebas sintéticas de Carga y E2E (Punto 3.c y 3.d)...'
                // bat 'locust -f load_tests/locustfile.py --headless -u 100 -r 10 --run-time 1m'
                echo 'Pendiente: Ejecución de Locust'
            }
        }
    }

    post {
        success {
            echo "El pipeline se completó y deplegó la versión ${APP_VERSION} correctamente."
        }
        failure {
            echo "Hubo un error en el flujo de CI/CD. Revisar logs de Gradle o Helm."
        }
    }
}
