"""JSON-file persistence for InMemorySecretaryRepository.

On Railway (or any stateless host) the in-memory repository is reset on every
process restart.  This module provides a PersistentRepository subclass that
serialises the full repository state to a single JSON file after every
mutation and reloads it on start-up.

File location (in priority order):
  1. SECRETARY_STATE_FILE env var
  2. /data/secretary_state.json  (Railway persistent volume, if mounted)
  3. /tmp/secretary_state.json   (fallback – survives container but not redeploy)
"""

from __future__ import annotations

import json
import logging
import os
from pathlib import Path
from typing import Any

from .models import (
    CompanyLegalIdentity,
    CompanyOperatingSettings,
    CompanyProfile,
    CRMRecord,
    LanguageScope,
    PasswordResetToken,
    TenantActivityOverrideRequest,
    TenantActivityPricing,
    TenantIndustryProfile,
    TenantLanguage,
    TenantLanguageChoice,
    TenantOperatingProfile,
    UserAccount,
    FirstInstallCreate,
    FirstInstallResult,
    FirstCompanyCreate,
    FirstAdminCreate,
)
from .repository import InMemorySecretaryRepository

logger = logging.getLogger(__name__)

_CANDIDATE_PATHS = [
    "/data/secretary_state.json",
    "/tmp/secretary_state.json",
]


def _resolve_state_path() -> Path:
    env = os.environ.get("SECRETARY_STATE_FILE")
    if env:
        return Path(env)
    for candidate in _CANDIDATE_PATHS:
        p = Path(candidate)
        try:
            p.parent.mkdir(parents=True, exist_ok=True)
            # Quick write-test
            test = p.parent / ".write_test"
            test.write_text("ok")
            test.unlink()
            return p
        except OSError:
            continue
    return Path("/tmp/secretary_state.json")


# ---------------------------------------------------------------------------
# Serialisation helpers
# ---------------------------------------------------------------------------

def _tuple_key(t: tuple) -> str:
    return "|".join(str(x) for x in t)


def _from_tuple_key_2(s: str) -> tuple[str, str]:
    parts = s.split("|", 1)
    return parts[0], parts[1]


def _from_tuple_key_3(s: str) -> tuple[str, str, str]:
    parts = s.split("|", 2)
    return parts[0], parts[1], parts[2]


def snapshot(repo: InMemorySecretaryRepository) -> dict[str, Any]:
    """Serialise the full repository state to a plain JSON-safe dict."""
    return {
        "companies": {k: v.model_dump(mode="json") for k, v in repo.companies.items()},
        "company_settings": {k: v.model_dump(mode="json") for k, v in repo.company_settings.items()},
        "tenant_operating_profiles": {k: v.model_dump(mode="json") for k, v in repo.tenant_operating_profiles.items()},
        "tenant_languages": {
            _tuple_key(k): v.model_dump(mode="json")
            for k, v in repo.tenant_languages.items()
        },
        "tenant_configuration": repo.tenant_configuration,
        "users": {k: v.model_dump(mode="json") for k, v in repo.users.items()},
        "password_hashes": repo.password_hashes,
        "tenant_industry_profiles": {k: v.model_dump(mode="json") for k, v in repo.tenant_industry_profiles.items()},
        "tenant_pricing": {
            _tuple_key(k): v.model_dump(mode="json")
            for k, v in repo.tenant_pricing.items()
        },
        "crm": {
            module: {rid: rec.model_dump(mode="json") for rid, rec in records.items()}
            for module, records in repo.crm.items()
        },
        "password_reset_tokens": {k: v.model_dump(mode="json") for k, v in repo.password_reset_tokens.items()},
    }


def restore(repo: InMemorySecretaryRepository, data: dict[str, Any]) -> None:
    """Restore a serialised snapshot into an existing (empty) repository."""
    repo.companies = {k: CompanyProfile.model_validate(v) for k, v in data.get("companies", {}).items()}
    repo.company_settings = {k: CompanyOperatingSettings.model_validate(v) for k, v in data.get("company_settings", {}).items()}
    repo.tenant_operating_profiles = {k: TenantOperatingProfile.model_validate(v) for k, v in data.get("tenant_operating_profiles", {}).items()}

    tl: dict = {}
    for raw_key, raw_val in data.get("tenant_languages", {}).items():
        company_id, scope_str, lang_code = _from_tuple_key_3(raw_key)
        tl[(company_id, LanguageScope(scope_str), lang_code)] = TenantLanguage.model_validate(raw_val)
    repo.tenant_languages = tl

    repo.tenant_configuration = data.get("tenant_configuration", {})
    repo.users = {k: UserAccount.model_validate(v) for k, v in data.get("users", {}).items()}
    repo.password_hashes = data.get("password_hashes", {})
    repo.tenant_industry_profiles = {k: TenantIndustryProfile.model_validate(v) for k, v in data.get("tenant_industry_profiles", {}).items()}

    tp: dict = {}
    for raw_key, raw_val in data.get("tenant_pricing", {}).items():
        company_id, activity_code = _from_tuple_key_2(raw_key)
        tp[(company_id, activity_code)] = TenantActivityPricing.model_validate(raw_val)
    repo.tenant_pricing = tp

    crm_modules = ("clients", "jobs", "tasks", "quotes", "invoices", "communications", "work_reports")
    crm_data = data.get("crm", {})
    repo.crm = {
        module: {rid: CRMRecord.model_validate(rec) for rid, rec in crm_data.get(module, {}).items()}
        for module in crm_modules
    }

    repo.password_reset_tokens = {k: PasswordResetToken.model_validate(v) for k, v in data.get("password_reset_tokens", {}).items()}


# ---------------------------------------------------------------------------
# Persistent subclass
# ---------------------------------------------------------------------------

class PersistentRepository(InMemorySecretaryRepository):
    """InMemorySecretaryRepository that persists state to a JSON file.

    Every mutating method calls _save() after the super() call so the on-disk
    snapshot stays in sync with in-memory state.
    """

    def __init__(self, state_file: Path | str | None = None) -> None:
        super().__init__()
        self._state_file: Path = Path(state_file) if state_file else _resolve_state_path()
        self._load()

    # ------------------------------------------------------------------
    # Private helpers
    # ------------------------------------------------------------------

    def _load(self) -> None:
        if not self._state_file.exists():
            logger.info("No state file at %s – starting fresh", self._state_file)
            return
        try:
            data = json.loads(self._state_file.read_text(encoding="utf-8"))
            restore(self, data)
            logger.info("Loaded state from %s (companies=%d, users=%d)",
                        self._state_file, len(self.companies), len(self.users))
        except Exception as exc:
            logger.warning("Failed to load state from %s: %s – starting fresh", self._state_file, exc)

    def _save(self) -> None:
        try:
            self._state_file.parent.mkdir(parents=True, exist_ok=True)
            tmp = self._state_file.with_suffix(".tmp")
            tmp.write_text(json.dumps(snapshot(self), indent=2, ensure_ascii=False), encoding="utf-8")
            tmp.replace(self._state_file)
        except Exception as exc:
            logger.error("Failed to persist state to %s: %s", self._state_file, exc)

    # ------------------------------------------------------------------
    # Mutating overrides – call super then _save
    # ------------------------------------------------------------------

    def create_first_company(self, payload: FirstCompanyCreate):
        result = super().create_first_company(payload)
        self._save()
        return result

    def create_first_admin(self, **kwargs):
        result = super().create_first_admin(**kwargs)
        self._save()
        return result

    def create_first_install(self, payload: FirstInstallCreate, **kwargs):
        result = super().create_first_install(payload, **kwargs)
        self._save()
        return result

    def update_company(self, company_id: str, profile: CompanyProfile) -> CompanyProfile:
        result = super().update_company(company_id, profile)
        self._save()
        return result

    def update_company_settings(self, company_id: str, settings: CompanyOperatingSettings) -> CompanyOperatingSettings:
        result = super().update_company_settings(company_id, settings)
        self._save()
        return result

    def update_company_legal_identity(self, company_id: str, identity: CompanyLegalIdentity) -> CompanyLegalIdentity:
        result = super().update_company_legal_identity(company_id, identity)
        self._save()
        return result

    def update_tenant_operating_profile(self, company_id: str, settings) -> TenantOperatingProfile:
        result = super().update_tenant_operating_profile(company_id, settings)
        self._save()
        return result

    def replace_tenant_languages(self, company_id: str, languages: list[TenantLanguageChoice]) -> list[TenantLanguage]:
        result = super().replace_tenant_languages(company_id, languages)
        self._save()
        return result

    def set_client_language(self, company_id: str, client_id: str, language_code: str):
        result = super().set_client_language(company_id, client_id, language_code)
        self._save()
        return result

    def save_tenant_pricing(self, company_id: str, activity_code: str, request: TenantActivityOverrideRequest) -> TenantActivityPricing:
        result = super().save_tenant_pricing(company_id, activity_code, request)
        self._save()
        return result

    def reset_tenant_pricing(self, company_id: str, activity_code: str) -> bool:
        result = super().reset_tenant_pricing(company_id, activity_code)
        self._save()
        return result

    def create_crm_record(self, module: str, company_id: str, name: str, data: dict) -> CRMRecord:
        result = super().create_crm_record(module, company_id, name, data)
        self._save()
        return result

    def create_password_reset_token(self, user: UserAccount, plain_token: str) -> PasswordResetToken:
        result = super().create_password_reset_token(user, plain_token)
        self._save()
        return result

    def mark_password_reset_token_used(self, token_id: str) -> None:
        super().mark_password_reset_token_used(token_id)
        self._save()

    def reset_user_password(self, user_id: str, new_password: str) -> None:
        super().reset_user_password(user_id, new_password)
        self._save()

    def admin_recovery_reset_password(self, email: str, new_password: str) -> UserAccount:
        result = super().admin_recovery_reset_password(email, new_password)
        self._save()
        return result
