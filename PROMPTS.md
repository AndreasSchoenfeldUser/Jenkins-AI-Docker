# PROMPTS — Jenkins über Claude Code steuern

Diese Datei sammelt die **natürlichsprachlichen Prompts**, mit denen sich die Jenkins-Instanz
aus Claude Code heraus über den MCP-Server [`mcp-server/jenkins_mcp.py`](mcp-server/jenkins_mcp.py)
bedienen lässt. Jeder Prompt löst ein konkretes MCP-Tool aus.

> **Voraussetzung:** Der MCP-Server `jenkins` ist in Claude Code registriert
> (siehe [Anleitung.md §6.2](Anleitung.md#62-in-claude-code-registrieren)) und mit
> `/mcp` als `connected` sichtbar.

---

## Übersicht — Prompt → Tool

| Prompt-Muster (deutsch)                                       | Aufgelöstes Tool             |
|---------------------------------------------------------------|------------------------------|
| „Welche Jenkins-Version läuft?" / „Status der Jenkins-Instanz"| `jenkins_version`            |
| „Liste alle Jobs" / „Welche Jobs gibt es?"                    | `jenkins_list_jobs`          |
| „Details zu Job *X*" / „Was kann Job *X*?"                    | `jenkins_get_job`            |
| „Starte einen Build von *X*"                                  | `jenkins_trigger_build`      |
| „Wie lief Build #*N* von *X*?"                                | `jenkins_get_build`          |
| „Zeig mir den Log von Build #*N* von *X*"                     | `jenkins_get_build_log`      |
| „Was steht in der Queue?" / „Was wartet auf Ausführung?"      | `jenkins_get_queue`          |
| „Welche Nodes / Agents sind verfügbar?"                       | `jenkins_get_nodes`          |
| „Starte Jenkins neu (safe restart)"                           | `jenkins_safe_restart`       |

---

## 1. Health- und Statusprompts

### „Welche Jenkins-Version läuft?"
- **Tool:** `jenkins_version`
- **Liefert:** Versions-String (X-Jenkins Header), Modus (`NORMAL`/`EXCLUSIVE`), Anzahl Executors, ob `quietingDown` aktiv ist.
- **Variationen:**
  - „Läuft Jenkins?"
  - „Status der Instanz"
  - „Wie viele Executors gibt es?"

### „Welche Nodes sind verfügbar?"
- **Tool:** `jenkins_get_nodes`
- **Liefert:** Liste aller Build-Nodes inkl. `offline`, `temporarilyOffline`, `numExecutors`, Monitoring-Daten (Disk, Memory, Arch).
- **Variationen:**
  - „Sind alle Agents online?"
  - „Welche Build-Knoten habe ich?"

---

## 2. Job-Discovery

### „Liste alle Jobs"
- **Tool:** `jenkins_list_jobs`
- **Liefert:** Name, URL, `color` (blau = letzter Build erfolgreich, rot = fehlgeschlagen, `notbuilt`, etc.) und Daten des letzten Builds (Nummer, Result, Timestamp).
- **Variationen:**
  - „Welche Pipelines existieren?"
  - „Zeig mir den aktuellen Status aller Jobs"

### „Details zu Job *X*"
- **Tool:** `jenkins_get_job(name='X')`
- **Liefert:** Beschreibung, `buildable`, `inQueue`, `nextBuildNumber`, `lastBuild`, Liste der vergangenen Builds.
- **Variationen:**
  - „Hat *X* einen aktuellen Build laufen?"
  - „Was war der letzte Build von *X*?"

---

## 3. Builds anstoßen und beobachten

### „Starte einen Build von *X*"
- **Tool:** `jenkins_trigger_build(name='X')`
- **Liefert:** Queue-URL des erzeugten Items (Buildnummer steht beim Triggern noch nicht fest).
- **Hinweis:** Der MCP-Server holt automatisch einen CSRF-Crumb.
- **Variationen:**
  - „Trigger den Job *X*"
  - „Build *X* anstoßen"

### „Starte einen Build von *X* mit Parameter *NAME=...*"
- **Tool:** `jenkins_trigger_build(name='X', parameters={'NAME': '...'})`
- **Wann anwendbar:** wenn der Job (z. B. `seed-demo`) Parameter deklariert.
- **Variationen:**
  - „Starte *seed-demo* mit NAME=Comquent"
  - „Build *X* mit folgenden Parametern: …"

### „Wie lief Build #*N* von *X*?"
- **Tool:** `jenkins_get_build(name='X', number=N)`
- **Liefert:** `result` (SUCCESS/FAILURE/UNSTABLE/ABORTED), `duration`, `timestamp`, `building`-Flag, Cause-Liste.
- **Variationen:**
  - „Status von Build 3 von hello-pipeline"
  - „Läuft Build #*N* noch?"

### „Zeig mir den Log von Build #*N* von *X*"
- **Tool:** `jenkins_get_build_log(name='X', number=N)`
- **Liefert:** Vollständiger Konsolen-Text, Antwortgröße (`size`) und Flag `more` für Streaming weiterer Chunks.
- **Variationen:**
  - „Konsolen-Output von Build #*N*"
  - „Warum ist Build #*N* fehlgeschlagen?" (Log lesen + interpretieren)

### „Was steht in der Queue?"
- **Tool:** `jenkins_get_queue`
- **Liefert:** Wartende/blockierte Items mit `task`, `why`, `inQueueSince`, `stuck`/`blocked`-Flags.
- **Variationen:**
  - „Wartet etwas auf Ausführung?"
  - „Sind Jobs hängen geblieben?"

---

## 4. Wartung

### „Starte Jenkins neu (safe restart)"
- **Tool:** `jenkins_safe_restart`
- **Verhalten:** Jenkins beendet laufende Builds nicht hart, sondern wartet, bis sie fertig sind, dann Restart.
- **Wann nutzen:** nach Plugin-Update oder Reload von JCasC-Änderungen, die einen Restart erfordern.
- **Variationen:**
  - „Safe-Restart der Instanz"
  - „Jenkins sauber neu starten"

> Für reine Konfigurations-Reloads (kein Restart nötig) bietet die JCasC-Webseite einen
> Reload-Button — alternativ als REST-Aufruf, der noch nicht als MCP-Tool exportiert ist.

---

## 5. Beispielsessions

### „Demo-Pipeline einmal durchlaufen lassen"
```
Du:    Starte einen Build von hello-pipeline und zeig mir am Ende den Log.

Claude wird (in dieser Reihenfolge):
  1. jenkins_trigger_build(name='hello-pipeline')
  2. jenkins_get_job(name='hello-pipeline')   # auf SUCCESS/FAILURE warten
  3. jenkins_get_build_log(name='hello-pipeline', number=<lastBuild.number>)
```

### „Build mit Parameter"
```
Du:    Trigger seed-demo mit NAME=Comquent und zeig mir das Ergebnis.

Claude wird:
  1. jenkins_trigger_build(name='seed-demo', parameters={'NAME': 'Comquent'})
  2. jenkins_get_build(name='seed-demo', number=<n>)
  3. jenkins_get_build_log(name='seed-demo', number=<n>)
```

### „Cluster-Check"
```
Du:    Ist alles okay? Versionen, Queue, Nodes.

Claude wird:
  1. jenkins_version
  2. jenkins_get_queue
  3. jenkins_get_nodes
```

---

## 6. Anti-Patterns

Diese Aufgaben sind **kein** Job für die MCP-Tools — sie gehören in den Code und brauchen
einen Git-Commit + Container-Restart:

| Wunsch                                  | Richtige Stelle                                |
|-----------------------------------------|------------------------------------------------|
| Job ändern / Pipeline-Code anpassen     | [casc/jenkins.yaml](casc/jenkins.yaml) oder [jobs/seed.groovy](jobs/seed.groovy) |
| Plugin hinzufügen                       | [plugins.txt](plugins.txt) + `docker-compose build` |
| Admin-User / Passwort wechseln          | `.env` + `docker-compose restart`              |
| System-Message ändern                   | [casc/jenkins.yaml](casc/jenkins.yaml) → `jenkins.systemMessage` |

> Faustregel: Wenn die Änderung **persistent über Container-Restarts hinweg** überleben
> soll, dann via Code, nicht per MCP/UI.

---

## 7. Sicherheits-/Sorgfalts-Hinweise

- **Schreibende Operationen** (`jenkins_trigger_build`, `jenkins_safe_restart`) sind
  effektiv unwiderruflich für die laufende Build-Queue. Bei produktiven Instanzen
  vor dem Ausführen Rückfrage stellen.
- **Logs können Secrets enthalten** — wenn ein Build Credentials nutzt, kann der
  Log-Output sie maskiert oder (bei Fehlkonfiguration) im Klartext enthalten. Vor
  dem Teilen / Posten gegenchecken.
- **`safe_restart` unterbricht eingehende UI-Sessions** — kein Datenverlust, aber
  Nutzer:innen werden kurz ausgeloggt.
