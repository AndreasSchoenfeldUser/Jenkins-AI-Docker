# Jenkins-Docker — Projekt-Leitfaden für Claude Code

Dieses Repository stellt eine vollständig in Code definierte Jenkins-Instanz in Docker bereit
und kann anschließend über die Jenkins-REST-API mit Claude Code (per MCP-Server) gesteuert werden.

## Ziele und Anforderungen

1. **Containerisiert**: Jenkins läuft ausschließlich in Docker (Image basierend auf `jenkins/jenkins:lts-jdk21`).
2. **Configuration-as-Code (JCasC)**: Die komplette Jenkins-Konfiguration ist deklarativ in
   [casc/jenkins.yaml](casc/jenkins.yaml) abgelegt. Keine manuelle Konfiguration über die UI.
3. **Pipeline-as-Code**: Jobs werden über Job-DSL ([jobs/seed.groovy](jobs/seed.groovy)) und
   Pipeline-Skripte ([pipelines/Jenkinsfile.example](pipelines/Jenkinsfile.example)) definiert.
4. **Plugins reproduzierbar**: Alle Plugins werden über [plugins.txt](plugins.txt) installiert.
5. **Setup-Wizard deaktiviert**: `JAVA_OPTS=-Djenkins.install.runSetupWizard=false`.
   Adminuser wird via JCasC + Secrets aus `.env` angelegt.
6. **Steuerung per MCP**: Ein lokaler MCP-Server in [mcp-server/](mcp-server/) stellt die
   wichtigsten Jenkins-REST-API-Operationen als Tools für Claude Code bereit.

## Verzeichnisstruktur

```
.
├── CLAUDE.md                 # Diese Datei — Anforderungen und Konventionen
├── Anleitung.md              # Bedienungsanleitung (Installation, Nutzung)
├── docker-compose.yml        # Stack-Definition (Jenkins-Service + Volumes + Netz)
├── Dockerfile                # Custom Jenkins-Image (Plugins + JCasC-Pfad)
├── plugins.txt               # Pinning der Jenkins-Plugins
├── casc/
│   └── jenkins.yaml          # Configuration-as-Code (Security, Jobs, Tools)
├── jobs/
│   └── seed.groovy           # Job-DSL: legt Demo-Pipeline-Jobs an
├── pipelines/
│   └── Jenkinsfile.example   # Referenz-Pipeline (deklarativ)
├── mcp-server/
│   ├── jenkins_mcp.py        # MCP-Server (Python, stdio)
│   ├── requirements.txt
│   └── README.md
├── .env.example              # Vorlage für Admin-Credentials
└── .gitignore
```

## Konventionen

- **Secrets**: Niemals in `casc/jenkins.yaml` hardcoden. Stattdessen `${ENV_VAR}` verwenden;
  Werte kommen aus der `.env`-Datei (nicht eingecheckt).
- **Plugins fixieren**: Versionen in [plugins.txt](plugins.txt) explizit pinnen, damit Builds reproduzierbar sind.
- **Job-Definition**: Neue Jobs entweder als Job-DSL in [jobs/](jobs/) oder direkt als
  `jobs:`-Block in [casc/jenkins.yaml](casc/jenkins.yaml). Keine UI-Änderungen.
- **Persistenz**: `jenkins_home` ist ein Named-Volume — beim Reset bewusst `docker-compose down -v` verwenden.
- **CSRF**: Alle schreibenden REST-Aufrufe benötigen einen Crumb; der MCP-Server holt ihn automatisch.

## MCP-Server (Steuerung durch Claude Code)

Der MCP-Server [mcp-server/jenkins_mcp.py](mcp-server/jenkins_mcp.py) stellt diese Tools bereit:

| Tool                       | Zweck                                                        |
|----------------------------|--------------------------------------------------------------|
| `jenkins_version`          | Version & Status der Instanz                                 |
| `jenkins_list_jobs`        | Alle Jobs auflisten                                          |
| `jenkins_get_job`          | Detail zu einem Job                                          |
| `jenkins_trigger_build`    | Build anstoßen (mit/ohne Parameter)                          |
| `jenkins_get_build`        | Build-Status (Nummer, Result, Duration)                      |
| `jenkins_get_build_log`    | Konsolen-Log eines Builds                                    |
| `jenkins_get_queue`        | Aktuelle Build-Queue                                         |
| `jenkins_get_nodes`        | Verfügbare Build-Nodes                                       |
| `jenkins_safe_restart`     | Safe-Restart der Instanz                                     |

Konfiguration über Environment-Variablen:
- `JENKINS_URL` (Default: `http://localhost:8080`)
- `JENKINS_USER` (Default: `admin`)
- `JENKINS_PASSWORD` oder `JENKINS_API_TOKEN`

Registrierung in Claude Code: siehe [Anleitung.md](Anleitung.md).

## Wenn du als Claude Code an diesem Projekt arbeitest

- **Konfigurationsänderungen**: Immer JCasC oder Job-DSL anpassen, nicht die laufende UI.
  Nach Änderungen an `casc/jenkins.yaml`: `docker-compose restart jenkins` (oder
  Reload-Config-Endpoint des MCP-Servers, sofern vorhanden).
- **Plugin hinzufügen**: in [plugins.txt](plugins.txt) ergänzen, dann
  `docker-compose build --no-cache jenkins && docker-compose up -d`.
- **Steuerung der laufenden Instanz**: bevorzugt über die MCP-Tools — nicht per
  direkten `curl`-Aufrufen, außer beim Debuggen.
- **Pipeline-Änderungen**: Den `Jenkinsfile` im Repo aktualisieren; der Job zieht ihn neu.
- **Reset**: `docker-compose down -v` löscht alle Daten — vorher rückfragen.
