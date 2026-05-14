# Anleitung — Jenkins in Docker mit Claude-Code-Steuerung

Diese Anleitung beschreibt Installation, Erststart, Bedienung und Anbindung an Claude Code.

---

## 1. Voraussetzungen

- Docker Desktop oder Docker Engine ≥ 24
- `docker-compose` (v2 oder v5)
- Python ≥ 3.10 für den MCP-Server (alternativ `uv`/`uvx`)
- Claude Code CLI (für die MCP-Anbindung)

Verfügbarkeit prüfen:

```bash
docker --version
docker-compose --version
python3 --version
```

---

## 2. Projekt aufsetzen

```bash
cd Jenkins-Docker
cp .env.example .env
# .env öffnen und JENKINS_ADMIN_PASSWORD setzen (kein Default-Wert!)
```

Inhalt der `.env` (Beispiel):

```env
JENKINS_ADMIN_ID=admin
JENKINS_ADMIN_PASSWORD=BitteAendern_2026!
```

---

## 3. Jenkins bauen und starten

```bash
docker-compose build
docker-compose up -d
```

Der erste Start kann 1–2 Minuten dauern (Plugin-Installation + JCasC-Anwendung).

Logs verfolgen:

```bash
docker-compose logs -f jenkins
```

Wenn `Jenkins is fully up and running` erscheint, ist die Instanz bereit.

Web-UI: <http://localhost:8080>
Login mit den Credentials aus der `.env`.

---

## 4. Was wird automatisch konfiguriert?

Durch [casc/jenkins.yaml](casc/jenkins.yaml) wird beim Start eingerichtet:

- **Security-Realm**: lokaler User aus `.env`
- **Authorization**: Logged-in users können alles, anonym nichts
- **Crumb-Issuer**: aktiviert (CSRF-Schutz)
- **System-Nachricht**: kennzeichnet die Instanz als „managed by JCasC"
- **Demo-Pipeline-Job** `hello-pipeline`: führt ein einfaches deklaratives Pipeline-Skript aus
- **Seed-Job** `seed`: legt zusätzliche Jobs aus [jobs/seed.groovy](jobs/seed.groovy) an

Änderungen an Konfiguration **immer in den YAML/Groovy-Dateien** vornehmen und Container
neu starten — nicht in der UI klicken.

```bash
docker-compose restart jenkins
```

---

## 5. Pipeline-Beispiele

### Inline-Pipeline (im JCasC, sofort verfügbar)
Der Job `hello-pipeline` enthält eine Pipeline direkt in der Konfiguration.
Build starten: in der UI klicken oder über die REST-API (siehe MCP-Tools).

### Pipeline aus dem Repo
Siehe [pipelines/Jenkinsfile.example](pipelines/Jenkinsfile.example).
Ein Pipeline-Job mit `pipelineFromScm` kann diese Datei aus einem Git-Repo ziehen.

---

## 6. MCP-Server für Claude Code

Der MCP-Server liegt unter [mcp-server/](mcp-server/) und stellt die Jenkins-REST-API
als Tools bereit, damit Claude Code die Instanz steuern kann.

### 6.1 Abhängigkeiten installieren

```bash
cd mcp-server
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

### 6.2 In Claude Code registrieren

```bash
claude mcp add jenkins \
  --scope user \
  --env JENKINS_URL=http://localhost:8080 \
  --env JENKINS_USER=admin \
  --env JENKINS_PASSWORD="$(grep JENKINS_ADMIN_PASSWORD .env | cut -d= -f2)" \
  -- \
  /absoluter/pfad/zu/Jenkins-Docker/mcp-server/.venv/bin/python \
  /absoluter/pfad/zu/Jenkins-Docker/mcp-server/jenkins_mcp.py
```

Den absoluten Pfad ggf. an dein System anpassen, z. B.:

```bash
PROJECT_DIR="$(pwd)"   # im Projekt-Root ausführen
claude mcp add jenkins \
  --scope user \
  --env JENKINS_URL=http://localhost:8080 \
  --env JENKINS_USER=admin \
  --env JENKINS_PASSWORD="$(grep JENKINS_ADMIN_PASSWORD .env | cut -d= -f2)" \
  -- \
  "$PROJECT_DIR/mcp-server/.venv/bin/python" \
  "$PROJECT_DIR/mcp-server/jenkins_mcp.py"
```

Anschließend in Claude Code prüfen:

```
/mcp
```

Dort sollte der Server `jenkins` mit Status `connected` auftauchen.

### 6.3 Verfügbare Tools

| Tool                       | Beispiel-Verwendung                                            |
|----------------------------|----------------------------------------------------------------|
| `jenkins_version`          | "Welche Jenkins-Version läuft?"                                |
| `jenkins_list_jobs`        | "Liste alle Jobs"                                              |
| `jenkins_get_job`          | "Status von Job `hello-pipeline`"                              |
| `jenkins_trigger_build`    | "Starte einen Build von `hello-pipeline`"                      |
| `jenkins_get_build`        | "Wie ist Build #3 von `hello-pipeline` gelaufen?"              |
| `jenkins_get_build_log`    | "Zeige mir den Log von Build #3"                               |
| `jenkins_get_queue`        | "Was steht in der Queue?"                                      |
| `jenkins_get_nodes`        | "Welche Nodes sind verfügbar?"                                 |
| `jenkins_safe_restart`     | "Starte Jenkins sicher neu"                                    |

---

## 7. Wartung

### Plugins aktualisieren
1. Versionen in [plugins.txt](plugins.txt) anpassen
2. Neu bauen: `docker-compose build --no-cache jenkins`
3. Hochfahren: `docker-compose up -d`

### Konfiguration neu laden (ohne Restart)
Die UI bietet unter *Manage Jenkins → Configuration as Code → Reload existing configuration*
einen Reload-Button. Identisch über die REST-API.

### Reset (Achtung — löscht alle Daten)
```bash
docker-compose down -v   # löscht das jenkins_home-Volume
```

### Backup
```bash
docker run --rm \
  -v jenkins-docker_jenkins_home:/data \
  -v "$(pwd)":/backup \
  alpine tar czf /backup/jenkins_home_$(date +%F).tgz -C /data .
```

---

## 8. Troubleshooting

| Symptom                                    | Ursache / Lösung                                                            |
|--------------------------------------------|-----------------------------------------------------------------------------|
| Container startet, UI zeigt Setup-Wizard   | `JAVA_OPTS=-Djenkins.install.runSetupWizard=false` fehlt (Dockerfile prüfen)|
| `Unable to find Jenkins admin user`        | `.env` nicht gesetzt oder Variablen nicht in `docker-compose.yml` durchgereicht|
| MCP-Tool antwortet mit 403                 | Crumb-Issue — Passwort/Token in MCP-Env prüfen                              |
| Plugin-Fehler beim Start                   | Inkompatible Plugin-Version in `plugins.txt`                                |
| MCP-Server `not connected` in Claude Code  | Pfad zur Python-`.venv` falsch oder Skript wirft beim Import einen Fehler   |

Container-Shell für Debugging:
```bash
docker-compose exec jenkins bash
```
