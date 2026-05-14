"""Jenkins MCP-Server.

Stellt zentrale Jenkins-REST-API-Operationen als Tools für Claude Code bereit.
Kommuniziert via stdio (Standard für lokale MCP-Server).
"""
from __future__ import annotations

import os
import sys
from typing import Any

import httpx
from mcp.server.fastmcp import FastMCP


JENKINS_URL = os.environ.get("JENKINS_URL", "http://localhost:8080").rstrip("/")
JENKINS_USER = os.environ.get("JENKINS_USER", "admin")
JENKINS_TOKEN = os.environ.get("JENKINS_API_TOKEN") or os.environ.get("JENKINS_PASSWORD")

if not JENKINS_TOKEN:
    print(
        "Warnung: weder JENKINS_API_TOKEN noch JENKINS_PASSWORD gesetzt — "
        "schreibende Aufrufe schlagen fehl.",
        file=sys.stderr,
    )

_auth = (JENKINS_USER, JENKINS_TOKEN) if JENKINS_TOKEN else None
_client = httpx.Client(base_url=JENKINS_URL, auth=_auth, timeout=30.0, follow_redirects=True)

mcp = FastMCP("jenkins")


def _get_crumb() -> dict[str, str]:
    """Holt einen CSRF-Crumb. Wirft httpx.HTTPError bei Fehlern."""
    r = _client.get("/crumbIssuer/api/json")
    r.raise_for_status()
    data = r.json()
    return {data["crumbRequestField"]: data["crumb"]}


def _json(path: str, **params: Any) -> dict:
    r = _client.get(path, params=params or None)
    r.raise_for_status()
    return r.json()


@mcp.tool()
def jenkins_version() -> dict:
    """Gibt Version, Modus und URL der Jenkins-Instanz zurück."""
    r = _client.get("/api/json")
    r.raise_for_status()
    version = r.headers.get("X-Jenkins", "unknown")
    data = r.json()
    return {
        "version": version,
        "url": JENKINS_URL,
        "mode": data.get("mode"),
        "numExecutors": data.get("numExecutors"),
        "quietingDown": data.get("quietingDown"),
    }


@mcp.tool()
def jenkins_list_jobs() -> list[dict]:
    """Listet alle Top-Level-Jobs mit Name, URL und letztem Build-Status."""
    data = _json("/api/json", tree="jobs[name,url,color,lastBuild[number,result,timestamp]]")
    return data.get("jobs", [])


@mcp.tool()
def jenkins_get_job(name: str) -> dict:
    """Details zu einem Job (Builds, nächste Buildnummer, Beschreibung)."""
    data = _json(
        f"/job/{name}/api/json",
        tree="name,url,description,buildable,inQueue,nextBuildNumber,"
             "lastBuild[number,result,timestamp,duration,url],"
             "builds[number,url,result]",
    )
    return data


@mcp.tool()
def jenkins_trigger_build(name: str, parameters: dict[str, str] | None = None) -> dict:
    """Stößt einen Build an. Bei parametrisierten Jobs `parameters` als dict übergeben.

    Gibt die Queue-Item-URL zurück (Buildnummer ist beim Triggern noch unbekannt).
    """
    headers = _get_crumb()
    if parameters:
        endpoint = f"/job/{name}/buildWithParameters"
        r = _client.post(endpoint, data=parameters, headers=headers)
    else:
        endpoint = f"/job/{name}/build"
        r = _client.post(endpoint, headers=headers)
    r.raise_for_status()
    return {
        "queued": True,
        "queue_url": r.headers.get("Location"),
        "status_code": r.status_code,
    }


@mcp.tool()
def jenkins_get_build(name: str, number: int) -> dict:
    """Status eines spezifischen Builds (Result, Dauer, Timestamp, Cause)."""
    data = _json(
        f"/job/{name}/{number}/api/json",
        tree="number,result,timestamp,duration,estimatedDuration,building,"
             "url,displayName,fullDisplayName,actions[causes[shortDescription]]",
    )
    return data


@mcp.tool()
def jenkins_get_build_log(name: str, number: int, start: int = 0) -> dict:
    """Konsolen-Log eines Builds (ab Byte-Offset `start`).

    Antwort enthält `text`, `size` (neues Offset) und `more` (ob noch Logs folgen).
    """
    r = _client.get(f"/job/{name}/{number}/logText/progressiveText", params={"start": start})
    r.raise_for_status()
    return {
        "text": r.text,
        "size": int(r.headers.get("X-Text-Size", "0")),
        "more": r.headers.get("X-More-Data", "false").lower() == "true",
    }


@mcp.tool()
def jenkins_get_queue() -> list[dict]:
    """Aktuelle Build-Queue."""
    data = _json(
        "/queue/api/json",
        tree="items[id,task[name,url],why,inQueueSince,stuck,blocked]",
    )
    return data.get("items", [])


@mcp.tool()
def jenkins_get_nodes() -> list[dict]:
    """Liste der Build-Nodes mit Status."""
    data = _json(
        "/computer/api/json",
        tree="computer[displayName,offline,temporarilyOffline,numExecutors,monitorData]",
    )
    return data.get("computer", [])


@mcp.tool()
def jenkins_safe_restart() -> dict:
    """Stößt einen Safe-Restart der Jenkins-Instanz an (wartet auf laufende Builds)."""
    headers = _get_crumb()
    r = _client.post("/safeRestart", headers=headers)
    if r.status_code in (200, 302, 503):
        return {"requested": True, "status_code": r.status_code}
    r.raise_for_status()
    return {"requested": False, "status_code": r.status_code}


if __name__ == "__main__":
    mcp.run()
