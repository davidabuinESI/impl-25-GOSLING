# Tema B: Delegación (Ruby) - Práctica IISS

Este proyecto demuestra el uso avanzado de **delegación** en Ruby mediante composición, módulos (`mixins`) y `Forwardable`, aplicando buenas prácticas del lenguaje y un enfoque desacoplado. El sistema implementa un **gestor de notificaciones** en una orquesta, donde diferentes canales (email, sms, push) delegan el método `preparar`.

## Estructura del proyecto

```
temas/
└── delegacion/
    └── ruby/
        ├── lib/
        │   ├── canales.rb           ← Clase colección con Enumerable
        │   ├── email.rb             ← Canal Email
        │   ├── sms.rb               ← Canal SMS
        │   ├── push.rb              ← Canal Push
        │   ├── notificador.rb       ← Clase base abstracta
        │   ├── usuario.rb           ← Usuario con delegación
        ├── spec/
        │   └── usuario_spec.rb      ← Pruebas unitarias con RSpec
        ├── Gemfile                  ← Declaración de dependencias
        ├── Rakefile                 ← Tarea default de tests
        ├── Dockerfile               ← Imagen personalizada para entorno Ruby
        ├── Jenkinsfile              ← Pipeline declarativo de Jenkins
        └── docs/
            ├── main.tf              ← Infraestructura Jenkins y DinD con Terraform
            ├── variables.tf
            ├── outputs.tf
            ├── providers.tf
            └── Dockerfile           ← Imagen Jenkins personalizada
```

---

## Objetivo del ejemplo

Sistema de notificaciones basado en delegación en Ruby. La orquesta mantiene una colección iterable de canales, y delega en ellos el comportamiento `preparar` y `enviar`. El diseño está desacoplado, orientado a interfaz, y flexible para admitir nuevas implementaciones.

Cada clase sigue los principios de encapsulamiento, bajo acoplamiento y alta cohesión.

---

## Ejecución del ejemplo

### 1. Construcción de la imagen Docker

Desde `temas/delegacion/ruby/docs`:

```bash
docker build -t myjenkins-ruby .
```

---

## Integración continua con Jenkins

Este proyecto se ejecuta automáticamente mediante un **pipeline declarativo de Jenkins** que ejecuta tests con RSpec y genera salidas verificables.

### 2. Despliegue de Jenkins con Terraform

Desde la carpeta `docs`:

```bash
terraform init
terraform apply
```

Esto lanza Jenkins y Docker-in-Docker con acceso compartido al socket de Docker.

### 3. Acceso a Jenkins

Ir a: [http://localhost:8081](http://localhost:8081)

#### Contraseña inicial

```bash
docker exec -it jenkins-blueocean cat /var/jenkins_home/secrets/initialAdminPassword
```

Una vez dentro:
1. Crear un nuevo ítem: `Delegacion-Ruby-Pipeline`
2. Tipo: **Pipeline**
3. En **Pipeline script from SCM**:
   - SCM: Git
   - URL: `https://github.com/uca-iiss/impl-25-McCARTHY`
   - Branch: `*/feature/delegacion-infra`
   - Script Path: `temas/delegacion/ruby/Jenkinsfile`

---

## Detalle de los archivos

### `canales.rb` [`lib/canales.rb`](./lib/canales.rb)

Contenedor iterable de canales de notificación. Usa `Enumerable` para exponer solo `each`, protegiendo la estructura interna.

```ruby
class Canales
  include Enumerable

  def initialize
    @canales = []
  end

  def each(&block)
    @canales.each(&block)
  end

  def agregar(canal)
    @canales << canal
  end

  def quitar(canal)
    @canales.delete(canal)
  end
end
```

### `email.rb`, `sms.rb`, `push.rb`

Cada clase representa un canal y define el método `preparar` y `enviar`.

```ruby
class Email
  def preparar
    puts "[Email] Estableciendo conexión SMTP..."
  end

  def enviar(mensaje)
    puts "[Email] Enviando mensaje: #{mensaje}"
  end
end
```

(Similares para SMS y Push)

### `notificador.rb` [`lib/notificador.rb`](./lib/notificador.rb)

Define la interfaz común para los canales. Facilita composición.

```ruby
class Notificador
  def preparar
    # Comportamiento por defecto opcional
  end

  def enviar(_mensaje)
    raise NotImplementedError, "Debe implementar #enviar"
  end
end
```

### `usuario.rb` [`lib/usuario.rb`](./lib/usuario.rb)

Delegación completa con `Forwardable`.

```ruby
require_relative 'canales'
require 'forwardable'

class Usuario
  extend Forwardable
  def_delegators :@canales, :agregar, :quitar, :each

  def initialize(nombre)
    @nombre = nombre
    @canales = Canales.new
  end

  def notificar_a_todos(mensaje)
    each do |canal|
      canal.preparar
      canal.enviar(mensaje)
    end
  end
end
```

### `usuario_spec.rb` [`spec/usuario_spec.rb`](./spec/usuario_spec.rb)

Test funcional del sistema completo:

```ruby
require_relative '../lib/usuario'
require_relative '../lib/email'

RSpec.describe Usuario do
  it 'envía notificaciones a través de todos los canales' do
    email = Email.new
    usuario = Usuario.new("Ana")
    usuario.agregar(email)

    expect {
      usuario.notificar_a_todos("Prueba")
    }.to output(/Enviando mensaje/).to_stdout
  end
end
```

---

## Infraestructura del Proyecto

### `Jenkinsfile` [`Jenkinsfile`](./Jenkinsfile)

```groovy
pipeline {
    agent {
        docker {
            image 'arevalo8/custom-delegacion'
        }
    }
    stages {
        stage('Test') {
            steps {
                sh 'bundle install'
                sh 'rspec spec/usuario_spec.rb --format documentation'
            }
            post {
                always {
                    junit 'results.xml'
                }
            }
        }
    }
}
```

---

### `Dockerfile` [`Dockerfile`](./Dockerfile)

```dockerfile
FROM ruby:3.2-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential && rm -rf /var/lib/apt/lists/*

COPY Gemfile Gemfile.lock ./
RUN gem install bundler && bundle install

COPY . .

CMD ["rake"]
```

---

### `docs/Dockerfile` [`docs/Dockerfile`](./docs/Dockerfile)

Imagen personalizada de Jenkins con Ruby, Bundler, RSpec y plugins para pipelines.

```dockerfile
FROM jenkins/jenkins:lts

USER root

RUN apt-get update && \
    apt-get install -y ruby ruby-dev build-essential curl docker.io && \
    gem install bundler rspec

RUN jenkins-plugin-cli --plugins blueocean docker-workflow

USER jenkins
```

---

### `main.tf`, `providers.tf`, `variables.tf`, `outputs.tf`

Configuración estándar para desplegar Jenkins con Docker-in-Docker usando volúmenes, certificados y red personalizada, similar al tema de lambdas.

---

## Archivos importantes

| Archivo                | Descripción                                                  |
|------------------------|--------------------------------------------------------------|
| `canales.rb`           | Colección iterable que encapsula los canales                 |
| `email.rb`, `sms.rb`   | Canales con lógica propia de preparación y envío             |
| `usuario.rb`           | Usuario que delega en sus canales mediante `Forwardable`     |
| `usuario_spec.rb`      | Pruebas unitarias con RSpec                                  |
| `Dockerfile`           | Imagen Docker personalizada con Ruby y Bundler               |
| `Jenkinsfile`          | Pipeline declarativo que ejecuta tests                       |
| `docs/*.tf`            | Infraestructura contenerizada de Jenkins con Terraform       |

---

## Limpieza de imágenes y contenedores

```bash
docker rm -f jenkins-blueocean
docker rm -f jenkins-docker
docker rmi myjenkins-ruby
```

---

## Resultado final

El sistema de notificaciones permite a cualquier `Usuario` añadir canales de forma dinámica y delegar tanto la preparación como el envío de mensajes, demostrando el principio de **delegación controlada** y diseño desacoplado en Ruby.
