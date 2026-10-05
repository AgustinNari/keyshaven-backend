# KeysHaven — Backend

API backend de KeysHaven, un marketplace de claves digitales desarrollado en equipo. Java 17, Spring Boot 3.5, Spring Security/JWT, JPA/Hibernate y MySQL. Gestiona usuarios y autenticación, productos, stock de claves digitales, descuentos y cupones, órdenes y reseñas. El frontend está en el repositorio hermano `Frontend-KeysHaven/keysHaven`.

## Configuración y ejecución

Requisitos: JDK 17+, MySQL y conexión para que Maven Wrapper descargue dependencias. Crear una base y un usuario local con permisos sobre ella. Usar una base separada para tests.

`.env.example` describe las variables. Spring Boot **no carga ese archivo automáticamente**: exportarlas en el shell antes de ejecutar. Ejemplo PowerShell, sustituyendo los valores ficticios:

```powershell
$env:DB_URL='jdbc:mysql://localhost:3306/marketplace?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true'
$env:DB_USERNAME='keyshaven_local'
$env:DB_PASSWORD='replace_with_local_password'
$env:JWT_SECRET=[guid]::NewGuid().ToString('N')+[guid]::NewGuid().ToString('N')
$env:CORS_ALLOWED_ORIGINS='http://localhost:5173'
cd marketplace
.\mvnw.cmd spring-boot:run
```

En Linux/macOS usar `export VARIABLE='valor'` y `./mvnw`. Mantener el mismo JWT_SECRET entre arranques si se desea conservar las sesiones. Nunca versionar credenciales reales.

Puerto por defecto: 4002. Opcionales: `SERVER_PORT`, `JWT_EXPIRATION_MS`, `DDL_AUTO`, `CORS_ALLOWED_ORIGINS`. El modo `DDL_AUTO=update` está pensado para el desarrollo local; no sustituye una estrategia de migraciones.

```powershell
.\mvnw.cmd clean verify
```

Los tests de integración necesitan las mismas variables apuntando a una **base de prueba aislada**. El registro público admite BUYER/SELLER. ADMIN debe provisionarse localmente por una persona autorizada; no existe registro público de administradores.

El flujo de pago es simulado y no procesa dinero ni transacciones reales.
