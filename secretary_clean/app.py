"""FastAPI app factory for the clean Secretary backend foundation."""

from __future__ import annotations

import os

from fastapi import FastAPI

from secretary_clean.api.routes import auth, bootstrap, catalogue, company, crm, language, tenant_pricing, users, voice
from secretary_clean.catalogue.source_parser import load_catalogue
from secretary_clean.core.repository import InMemorySecretaryRepository


def _default_repository() -> InMemorySecretaryRepository:
    """Return PersistentRepository on Railway / when SECRETARY_PERSISTENT=1, else in-memory."""
    use_persistent = (
        os.getenv("SECRETARY_PERSISTENT", "").lower() in ("1", "true", "yes")
        or os.getenv("RAILWAY_ENVIRONMENT") is not None
    )
    if use_persistent:
        from secretary_clean.core.persistence import PersistentRepository
        return PersistentRepository()
    return InMemorySecretaryRepository()


def create_app(repository: InMemorySecretaryRepository | None = None) -> FastAPI:
    app = FastAPI(title="Secretary Clean Backend", version="0.1.0")
    app.state.repository = repository if repository is not None else _default_repository()
    app.state.catalogue = load_catalogue()
    app.include_router(bootstrap.router, prefix="/api/v1")
    app.include_router(auth.router, prefix="/api/v1")
    app.include_router(company.router, prefix="/api/v1")
    app.include_router(users.router, prefix="/api/v1")
    app.include_router(catalogue.router, prefix="/api/v1")
    app.include_router(language.router, prefix="/api/v1")
    app.include_router(tenant_pricing.router, prefix="/api/v1")
    app.include_router(crm.router, prefix="/api/v1")
    app.include_router(voice.router, prefix="/api/v1")
    return app


app = create_app()
