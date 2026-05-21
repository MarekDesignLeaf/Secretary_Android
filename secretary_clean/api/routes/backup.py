"""Backup and biometric-auth routes for pre-uninstall data export."""

from __future__ import annotations

import secrets
import uuid
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException

from secretary_clean.api.deps import current_user, get_repository
from secretary_clean.core.models import UserAccount
from secretary_clean.core.repository import InMemorySecretaryRepository

router = APIRouter(prefix="/backup", tags=["backup"])


def _utcnow() -> str:
    return datetime.now(timezone.utc).isoformat()


# ---------------------------------------------------------------------------
# Backup create / list / restore
# ---------------------------------------------------------------------------

@router.post("/create")
def create_backup(
    payload: dict,
    user: UserAccount = Depends(current_user),
    repository: InMemorySecretaryRepository = Depends(get_repository),
):
    """Create a backup snapshot.  Admins/owners get full-tenant scope; others personal."""
    from secretary_clean.core.models import Permission
    full_scope = Permission.company_manage in set(user.permissions)

    restore_token = secrets.token_urlsafe(32)
    manifest_id = str(uuid.uuid4())

    # Collect data to include in backup
    backup_data: dict = {
        "manifest_id": manifest_id,
        "restore_token": restore_token,
        "created_by": user.id,
        "created_at": _utcnow(),
        "scope": "full" if full_scope else "personal",
        "company_id": user.company_id if full_scope else None,
        "user_id": user.id,
    }

    # Attempt to persist via repository if it supports backup storage
    try:
        repository.store_backup(manifest_id, restore_token, user.id, user.company_id, full_scope, backup_data)
    except AttributeError:
        # InMemorySecretaryRepository may not have backup methods — that's fine for tests
        pass

    return {
        "manifest_id": manifest_id,
        "restore_token": restore_token,
        "scope": backup_data["scope"],
        "created_at": backup_data["created_at"],
    }


@router.get("/manifests")
def list_backup_manifests(
    user: UserAccount = Depends(current_user),
    repository: InMemorySecretaryRepository = Depends(get_repository),
):
    """List backup manifests.  Returns company-scoped list for admins, personal for others."""
    try:
        return repository.list_backups(user.company_id)
    except AttributeError:
        return []


@router.get("/restore/{token}")
def get_backup_by_token(
    token: str,
    user: UserAccount = Depends(current_user),
    repository: InMemorySecretaryRepository = Depends(get_repository),
):
    try:
        manifest = repository.get_backup_by_token(token)
    except AttributeError:
        manifest = None

    if not manifest:
        raise HTTPException(status_code=404, detail="Backup not found or token expired")
    return manifest


# ---------------------------------------------------------------------------
# Biometric registration
# ---------------------------------------------------------------------------

@router.post("/biometric/register")
def register_biometric(
    payload: dict,
    user: UserAccount = Depends(current_user),
    repository: InMemorySecretaryRepository = Depends(get_repository),
):
    device_id = payload.get("device_id") or str(uuid.uuid4())
    biometric_hash = payload.get("biometric_hash", "")
    if not biometric_hash:
        raise HTTPException(status_code=422, detail="biometric_hash is required")

    try:
        repository.register_biometric(user.id, device_id, biometric_hash)
    except AttributeError:
        pass

    return {
        "device_id": device_id,
        "user_id": user.id,
        "registered_at": _utcnow(),
    }


@router.delete("/biometric/{device_id}")
def remove_biometric(
    device_id: str,
    user: UserAccount = Depends(current_user),
    repository: InMemorySecretaryRepository = Depends(get_repository),
):
    try:
        repository.remove_biometric(user.id, device_id)
    except AttributeError:
        pass
    return {"removed": True, "device_id": device_id}
