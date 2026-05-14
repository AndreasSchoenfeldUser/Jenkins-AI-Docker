# Jenkins MCP-Server

Lokaler MCP-Server (stdio) für die Jenkins-REST-API. Wird von Claude Code aufgerufen.

## Setup

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

## Environment-Variablen

| Variable             | Default                  | Bedeutung                              |
|----------------------|--------------------------|----------------------------------------|
| `JENKINS_URL`        | `http://localhost:8080`  | Basis-URL der Instanz                  |
| `JENKINS_USER`       | `admin`                  | Benutzer-ID                            |
| `JENKINS_PASSWORD`   | —                        | Passwort (Basic Auth)                  |
| `JENKINS_API_TOKEN`  | —                        | API-Token (bevorzugt, falls vorhanden) |

`JENKINS_API_TOKEN` hat Vorrang vor `JENKINS_PASSWORD`.

## Lokaler Test (ohne Claude Code)

```bash
JENKINS_PASSWORD=... python3 jenkins_mcp.py
# stdin/stdout — fertig zum Mount via Claude Code MCP
```

## Registrierung in Claude Code

Siehe [../Anleitung.md](../Anleitung.md), Abschnitt 6.2.
