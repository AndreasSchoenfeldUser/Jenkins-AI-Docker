# Jenkins-Docker — Configuration as Code & MCP-Steuerung

Eine vollständig in Code definierte Jenkins-Instanz in Docker, die sich anschließend
direkt aus [Claude Code](https://docs.claude.com/en/docs/claude-code/overview) über
die Jenkins-REST-API steuern lässt — via mitgeliefertem MCP-Server.

> **Worum geht's?**
> Jenkins ohne Klick-Setup: Installation, Security und Jobs werden über
> [Configuration-as-Code (JCasC)](https://www.jenkins.io/projects/jcasc/) und
> [Job-DSL](https://plugins.jenkins.io/job-dsl/) deklariert, der Container per
> `docker-compose` gestartet. Die laufende Instanz wird über einen lokalen
> MCP-Server (stdio) zum First-Class-Bürger in Claude Code — Jobs auflisten,
> Builds anstoßen, Logs holen, alles aus dem Chat heraus.

---

## Features

- **Reproduzierbares Image** — `jenkins/jenkins:lts-jdk21` + gepinnte Plugins über [plugins.txt](plugins.txt)
- **Setup-Wizard aus** — Administrator wird via JCasC aus `.env` angelegt
- **Demo-Jobs out-of-the-box** — inline-Pipeline (`hello-pipeline`), Job-DSL-Seed
  (`seed-demo`) und ein Docker-Pipeline-Demo (`python-docker-demo`), der ein
  Python-Image über die Docker-Pipeline-API (`docker.build` / `image.inside`) baut
  und ausführt
- **Pipeline Graph View als Default-Ansicht** — auf Build- und Job-Seite, statt
  klassischer Stage View (per JCasC fixiert)
- **MCP-Server für Claude Code** — 9 Tools für Version, Jobs, Builds, Logs, Queue, Nodes, Safe-Restart
- **Resiliente Plugin-Installation** — bezieht Plugins direkt von `archives.jenkins.io`,
  unabhängig von den geo-redirected Mirror-Hosts
- **Persistenz** — `jenkins_home` als Named-Volume

## Architektur

```
                  ┌─────────────────────┐
                  │      Claude Code    │
                  └──────────┬──────────┘
                             │ stdio (MCP)
                  ┌──────────▼──────────┐
                  │  mcp-server (Python)│
                  └──────────┬──────────┘
                             │ HTTPS REST + CSRF-Crumb
                  ┌──────────▼──────────┐
                  │  Jenkins-Container  │   <─ JCasC YAML
                  │  (jenkins-jcasc)    │   <─ Job-DSL Groovy
                  └─────────────────────┘
```

## Schnellstart

```bash
git clone <repo-url> Jenkins-Docker
cd Jenkins-Docker

cp .env.example .env           # JENKINS_ADMIN_PASSWORD anpassen!

docker-compose build
docker-compose up -d

# MCP-Server-Abhängigkeiten
python3 -m venv mcp-server/.venv
mcp-server/.venv/bin/pip install -r mcp-server/requirements.txt
```

Web-UI: <http://localhost:8080> — Login mit den Credentials aus der `.env`.

**MCP-Server in Claude Code registrieren**:

```bash
PASS="$(grep '^JENKINS_ADMIN_PASSWORD=' .env | cut -d= -f2-)"
claude mcp add jenkins --scope user \
  --env JENKINS_URL=http://localhost:8080 \
  --env JENKINS_USER=admin \
  --env JENKINS_PASSWORD="$PASS" \
  -- \
  "$(pwd)/mcp-server/.venv/bin/python" "$(pwd)/mcp-server/jenkins_mcp.py"
```

Anschließend `/mcp` in Claude Code → `jenkins` sollte `connected` sein.

Detaillierte Installations- und Bedienungsanleitung: **[Anleitung.md](Anleitung.md)**.

## Verfügbare MCP-Tools

| Tool                       | Zweck                                                        |
|----------------------------|--------------------------------------------------------------|
| `jenkins_version`          | Version & Status der Instanz                                 |
| `jenkins_list_jobs`        | Alle Jobs auflisten                                          |
| `jenkins_get_job`          | Detail zu einem Job                                          |
| `jenkins_trigger_build`    | Build anstoßen (mit/ohne Parameter)                          |
| `jenkins_get_build`        | Build-Status                                                 |
| `jenkins_get_build_log`    | Konsolen-Log                                                 |
| `jenkins_get_queue`        | Build-Queue                                                  |
| `jenkins_get_nodes`        | Build-Nodes                                                  |
| `jenkins_safe_restart`     | Safe-Restart der Instanz                                     |

Eine kuratierte Sammlung natürlichsprachlicher Prompts, die diese Tools auslösen
(inkl. Beispielsessions und Anti-Patterns), findet sich in **[PROMPTS.md](PROMPTS.md)**.

## Repository-Struktur

```
.
├── README.md                 # Diese Datei
├── CLAUDE.md                 # Anforderungen & Konventionen (für Claude Code)
├── Anleitung.md              # Bedienungsanleitung
├── PROMPTS.md                # Prompt-Katalog: Jenkins via Claude Code steuern
├── docker-compose.yml
├── Dockerfile
├── plugins.txt
├── .env.example
├── casc/jenkins.yaml         # Configuration-as-Code
├── jobs/
│   ├── seed.groovy          # Job-DSL (seed-demo)
│   └── python_docker_demo.groovy  # Docker-Pipeline-API Demo
├── pipelines/Jenkinsfile.example
└── mcp-server/               # Python-MCP-Server für die Jenkins-REST-API
    ├── jenkins_mcp.py
    ├── requirements.txt
    └── README.md
```

## Voraussetzungen

- Docker Engine ≥ 24 / Docker Desktop
- `docker-compose` (v2 oder v5)
- Python ≥ 3.10 (für den MCP-Server)
- [Claude Code](https://docs.claude.com/en/docs/claude-code/overview) (für die MCP-Anbindung)

## Konventionen

- **Keine UI-Konfiguration** — alle Änderungen über YAML/Groovy im Repo, danach
  `docker-compose restart jenkins`.
- **Secrets niemals in JCasC** — `${ENV_VAR}` und Werte aus `.env`.
- **Plugin-Versionen pinnen** — in `plugins.txt`.

Details siehe [CLAUDE.md](CLAUDE.md).

## Änderungen

### 2026-05-15

- **Docker-Pipeline-API**: Plugin `docker-workflow` ergänzt. Der Job
  [`python-docker-demo`](jobs/python_docker_demo.groovy) baut und startet sein
  Image jetzt über `docker.build(tag, '.')` und `docker.image(tag).inside { … }`
  statt über direkte `sh 'docker …'`-Aufrufe.
- **Pipeline Graph View statt Stage View**: Plugin `pipeline-graph-view`
  ergänzt, `pipeline-stage-view` entfernt. Die Anzeige ist über JCasC fixiert
  ([casc/jenkins.yaml](casc/jenkins.yaml)):

  ```yaml
  appearance:
    pipelineGraphView:
      showGraphOnBuildPage: true
      showGraphOnJobPage: true
      showStageDurations: true
      showStageNames: true
  ```

  Damit bleibt die Einstellung auch nach `docker-compose down -v` erhalten —
  kein manueller Klick unter *Manage Jenkins → Appearance* mehr nötig.

## Lizenz

Frei verwendbar als Vorlage. Jenkins und alle eingebundenen Plugins stehen unter ihren
jeweiligen Lizenzen (überwiegend MIT/Apache-2.0).

---

## Über Comquent

Dieses Projekt entstand im Kontext der Arbeit der **[Comquent GmbH](https://www.comquent.de)** —
Spezialisten für **Continuous Delivery, DevOps und Software-Qualität**.
Wir unterstützen Unternehmen beim Aufbau effizienter CI/CD-Pipelines, bei der
Einführung von Configuration-as-Code-Praktiken sowie bei der sicheren Integration
von KI-Werkzeugen in Entwicklungs- und Build-Prozesse.

🌐 [comquent.de](https://www.comquent.de)
