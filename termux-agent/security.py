"""Standalone, fail-closed authorization policy for the Termux agent.

Only trusted entry handlers may call create_request_context(), after authenticating
the caller and assigning its principal and session. Derive source from the handler
and invocation from the code path, never from model output or request JSON.
A tool selected by a model always has Invocation.MODEL, even when the original
request came from a human. DIRECT means an explicit tool request parsed by a
trusted handler; it is not a confirmation of an earlier model proposal.

This module does not authenticate transports, validate tool arguments, execute
tools, or implement action confirmation. Those remain the caller's responsibility.
The private issuer prevents accidental context construction, not hostile Python
code running in the same process. Do not expose the factory as a model tool.
"""
from __future__ import annotations

from dataclasses import InitVar, dataclass
from enum import Enum
from types import MappingProxyType
from typing import Mapping


class RequestSource(Enum):
    CLI = "cli"
    VOICE = "voice"
    TELEGRAM = "telegram"
    BRIDGE = "bridge"


class Invocation(Enum):
    DIRECT = "direct"
    MODEL = "model"


class AccessCategory(Enum):
    READ_ONLY = "read_only"
    SENSITIVE_READ = "sensitive_read"
    WRITE = "write"
    EXECUTE = "execute"
    EXTERNAL_ACTION = "external_action"


_CONTEXT_ISSUER = object()


@dataclass(frozen=True)
class RequestContext:
    """Immutable identity and provenance supplied exclusively by trusted handlers."""

    source: RequestSource
    invocation: Invocation
    principal: str
    session_id: str
    _issuer: InitVar[object] = None

    def __post_init__(self, _issuer: object) -> None:
        if _issuer is not _CONTEXT_ISSUER:
            raise ValueError("Use create_request_context() in a trusted entry handler.")
        if type(self.source) is not RequestSource:
            raise ValueError("Unknown request source.")
        if type(self.invocation) is not Invocation:
            raise ValueError("Unknown invocation.")
        if type(self.principal) is not str or not self.principal.strip():
            raise ValueError("A verified, nonempty principal is required.")
        if type(self.session_id) is not str or not self.session_id.strip():
            raise ValueError("A nonempty session ID is required.")


def create_request_context(
    *,
    source: RequestSource,
    invocation: Invocation,
    principal: str,
    session_id: str,
) -> RequestContext:
    """Trusted-handler API; never populate these fields from untrusted payloads.

    The handler must authenticate and authorize the principal for the transport.
    For CLI/voice, establish the local user; for Telegram/bridge, validate the
    remote identity and access policy first. Session IDs are assigned by trusted
    code. This function validates structure only and is not authentication.
    """
    return RequestContext(
        source=source,
        invocation=invocation,
        principal=principal,
        session_id=session_id,
        _issuer=_CONTEXT_ISSUER,
    )


@dataclass(frozen=True)
class ToolSpec:
    """Trusted, static tool metadata; no sources are allowed unless listed."""

    name: str
    description: str
    category: AccessCategory
    allowed_sources: frozenset[RequestSource] = frozenset()

    def __post_init__(self) -> None:
        if type(self.name) is not str or not self.name.strip():
            raise ValueError("A nonempty tool name is required.")
        if type(self.description) is not str or not self.description.strip():
            raise ValueError("A nonempty tool description is required.")
        if type(self.category) is not AccessCategory:
            raise ValueError("Unknown access category.")
        sources = frozenset(self.allowed_sources)
        if any(type(source) is not RequestSource for source in sources):
            raise ValueError("Unknown allowed source.")
        object.__setattr__(self, "allowed_sources", sources)


@dataclass(frozen=True)
class AuthorizationDecision:
    allowed: bool
    reason: str

    def __bool__(self) -> bool:
        return self.allowed


# Enumerate sources explicitly: adding a source must not silently grant access.
_ENTRY_SOURCES = frozenset({
    RequestSource.CLI,
    RequestSource.VOICE,
    RequestSource.TELEGRAM,
    RequestSource.BRIDGE,
})

# This registry is code-owned. Never extend it using model or request data.
TOOL_SPECS: Mapping[str, ToolSpec] = MappingProxyType({
    "device_status": ToolSpec(
        "device_status", "Read device and network status.",
        AccessCategory.SENSITIVE_READ, _ENTRY_SOURCES,
    ),
    "list_files": ToolSpec(
        "list_files", "List names inside the bounded workspace.",
        AccessCategory.READ_ONLY, _ENTRY_SOURCES,
    ),
    "workspace_summary": ToolSpec(
        "workspace_summary", "Count Python files and directories in a bounded workspace folder.",
        AccessCategory.READ_ONLY, _ENTRY_SOURCES,
    ),
    "read_file": ToolSpec(
        "read_file", "Read potentially private workspace contents.",
        AccessCategory.SENSITIVE_READ, _ENTRY_SOURCES,
    ),
    "write_file": ToolSpec(
        "write_file", "Create a new workspace file.",
        AccessCategory.WRITE, _ENTRY_SOURCES,
    ),
    "safe_command": ToolSpec(
        "safe_command", "Run a command from the executor's fixed allowlist.",
        AccessCategory.EXECUTE, _ENTRY_SOURCES,
    ),
    "open_url": ToolSpec(
        "open_url", "Open an external HTTP or HTTPS URL.",
        AccessCategory.EXTERNAL_ACTION, _ENTRY_SOURCES,
    ),
    "memory_add": ToolSpec(
        "memory_add", "Persist an explicitly supplied memory.",
        AccessCategory.WRITE, _ENTRY_SOURCES,
    ),
    "memory_search": ToolSpec(
        "memory_search", "Read potentially private stored memories.",
        AccessCategory.SENSITIVE_READ, _ENTRY_SOURCES,
    ),
    "clipboard_get": ToolSpec(
        "clipboard_get", "Read private clipboard contents.",
        AccessCategory.SENSITIVE_READ, _ENTRY_SOURCES,
    ),
    "clipboard_set": ToolSpec(
        "clipboard_set", "Replace clipboard contents.",
        AccessCategory.WRITE, _ENTRY_SOURCES,
    ),
    "launch_app": ToolSpec(
        "launch_app", "Launch an application from the executor's allowlist.",
        AccessCategory.EXECUTE, _ENTRY_SOURCES,
    ),
})

_DIRECT_CATEGORIES = frozenset({
    AccessCategory.READ_ONLY,
    AccessCategory.SENSITIVE_READ,
    AccessCategory.WRITE,
    AccessCategory.EXECUTE,
    AccessCategory.EXTERNAL_ACTION,
})


def authorize_tool(
    tool_name: str,
    context: RequestContext | None = None,
) -> AuthorizationDecision:
    """Return a deterministic decision without I/O, mutation, or tool execution.

    Unknown names, missing/invalid context, sources, invocations and categories
    fail closed. Model calls and direct Telegram/bridge calls can only use
    READ_ONLY tools. No request arguments or JSON confirmation flags are
    accepted, and a model proposal cannot upgrade
    itself to DIRECT. An allow decision still requires executor-side argument,
    path, command and application validation.
    """
    if type(tool_name) is not str or tool_name not in TOOL_SPECS:
        return AuthorizationDecision(False, "unknown_tool")
    if context is None:
        return AuthorizationDecision(False, "missing_context")
    if type(context) is not RequestContext:
        return AuthorizationDecision(False, "invalid_context")
    if type(context.source) is not RequestSource:
        return AuthorizationDecision(False, "unknown_source")
    if type(context.invocation) is not Invocation:
        return AuthorizationDecision(False, "unknown_invocation")
    if type(context.principal) is not str or not context.principal.strip():
        return AuthorizationDecision(False, "missing_principal")
    if type(context.session_id) is not str or not context.session_id.strip():
        return AuthorizationDecision(False, "missing_session")

    spec = TOOL_SPECS[tool_name]
    if context.source not in spec.allowed_sources:
        return AuthorizationDecision(False, "source_not_allowed")
    if type(spec.category) is not AccessCategory:
        return AuthorizationDecision(False, "unknown_category")
    if context.invocation is Invocation.MODEL:
        if spec.category is AccessCategory.READ_ONLY:
            return AuthorizationDecision(True, "allowed_read_only_model_call")
        return AuthorizationDecision(False, "direct_request_required")
    if context.invocation is Invocation.DIRECT:
        if context.source in (RequestSource.TELEGRAM, RequestSource.BRIDGE):
            if spec.category is AccessCategory.READ_ONLY:
                return AuthorizationDecision(True, "allowed_read_only_remote_call")
            return AuthorizationDecision(False, "remote_source_read_only")
        if spec.category in _DIRECT_CATEGORIES:
            return AuthorizationDecision(True, "allowed_direct_request")
    return AuthorizationDecision(False, "denied_by_default")
