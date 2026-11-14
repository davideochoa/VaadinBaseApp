@echo off
REM Staged startup for small machines: starts the stack in layers so a slow CPU
REM doesn't have Postgres, Zookeeper, Kafka and 4 JVMs all competing at once.
REM Run from anywhere; it switches to the repo root first.
cd /d "%~dp0.."
if not exist .env copy .env.example .env >nul
set F=-f docker-compose.yml -f docker-compose.lowmem.yml

echo [1/4] Databases + Zookeeper
docker compose %F% up -d --build --wait --wait-timeout 300 db-users db-security db-audit zookeeper || goto :fail
echo [2/4] Kafka
docker compose %F% up -d --wait --wait-timeout 400 kafka || goto :fail
echo [3/4] Microservices
docker compose %F% up -d --build --wait --wait-timeout 600 ms-users || goto :fail
docker compose %F% up -d --build ms-security ms-audit || goto :fail
echo [4/4] app-vaadin (first start compiles the frontend: can take several minutes)
docker compose %F% up -d --build app-vaadin || goto :fail

echo.
echo Done. Follow progress with: docker compose %F% logs -f app-vaadin
echo App: http://localhost:8080  (admin / admin)
exit /b 0

:fail
echo.
echo Startup step failed. Inspect with: docker compose %F% ps -a
echo and: docker compose %F% logs --tail 60 ^<service^>
exit /b 1
