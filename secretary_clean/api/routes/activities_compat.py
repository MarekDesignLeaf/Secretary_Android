"""Compatibility routes for the Android activities/pricing API.

The Android client uses numeric (Long) IDs for groups, subtypes and templates.
We derive stable CRC32-based numeric IDs from the string codes so every call
is deterministic and requires no database sequence.
"""

from __future__ import annotations

import binascii
from typing import Any

from fastapi import APIRouter, Depends, HTTPException, Query, Request

from secretary_clean.api.deps import current_user, get_repository
from secretary_clean.core.models import UserAccount
from secretary_clean.core.repository import InMemorySecretaryRepository

router = APIRouter(prefix="/activities", tags=["activities-compat"])


def _crc32_id(code: str) -> int:
    """Return a stable positive 32-bit integer derived from a string code."""
    return binascii.crc32(code.encode()) & 0x7FFF_FFFF


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _industry_by_id(request: Request, group_id: int):
    for industry in request.app.state.catalogue.industries:
        if _crc32_id(industry.code) == group_id:
            return industry
    return None


def _subtype_by_id(request: Request, subtype_id: int):
    for industry in request.app.state.catalogue.industries:
        for subtype in industry.subtypes:
            if _crc32_id(subtype.code) == subtype_id:
                return subtype
    return None


def _activity_by_id(request: Request, template_id: int):
    for industry in request.app.state.catalogue.industries:
        for subtype in industry.subtypes:
            for activity in subtype.activities:
                if _crc32_id(activity.code) == template_id:
                    return activity
    return None


def _activity_to_template(activity, subtype_code: str = "") -> dict[str, Any]:
    return {
        "id": _crc32_id(activity.code),
        "code": activity.code,
        "name": activity.name,
        "industry_code": activity.industry_code,
        "subtype_code": activity.subtype_code or subtype_code,
        "group_id": _crc32_id(activity.industry_code),
        "subtype_id": _crc32_id(activity.subtype_code or subtype_code),
        "default_pricing_method_code": activity.default_pricing_method_code,
        "available_pricing_method_codes": activity.available_pricing_method_codes,
    }


# ---------------------------------------------------------------------------
# Catalogue shape (groups / subtypes)
# ---------------------------------------------------------------------------

@router.get("/groups")
def get_industry_groups(request: Request, user: UserAccount = Depends(current_user)):
    return [
        {
            "id": _crc32_id(industry.code),
            "code": industry.code,
            "name": industry.name,
            "display_order": industry.display_order,
        }
        for industry in request.app.state.catalogue.industries
    ]


@router.get("/subtypes/{group_id}")
def get_industry_subtypes(group_id: int, request: Request, user: UserAccount = Depends(current_user)):
    industry = _industry_by_id(request, group_id)
    if not industry:
        raise HTTPException(status_code=404, detail="Industry group not found")
    return [
        {
            "id": _crc32_id(subtype.code),
            "code": subtype.code,
            "name": subtype.name,
            "industry_code": subtype.industry_code,
            "group_id": group_id,
            "display_order": subtype.display_order,
        }
        for subtype in industry.subtypes
    ]


# ---------------------------------------------------------------------------
# Activity templates
# ---------------------------------------------------------------------------

@router.get("/templates")
def get_activity_templates(
    request: Request,
    user: UserAccount = Depends(current_user),
    subtype_code: str | None = Query(default=None),
    group_code: str | None = Query(default=None),
    subtype_id: int | None = Query(default=None),
    group_id: int | None = Query(default=None),
):
    results = []
    for industry in request.app.state.catalogue.industries:
        if group_code and industry.code != group_code:
            continue
        if group_id and _crc32_id(industry.code) != group_id:
            continue
        for subtype in industry.subtypes:
            if subtype_code and subtype.code != subtype_code:
                continue
            if subtype_id and _crc32_id(subtype.code) != subtype_id:
                continue
            for activity in subtype.activities:
                results.append(_activity_to_template(activity))
    return results


# ---------------------------------------------------------------------------
# Tenant pricing (per-activity overrides)
# ---------------------------------------------------------------------------

@router.get("/tenant/{tenant_id}")
def get_tenant_activity_pricing(
    tenant_id: int,
    request: Request,
    user: UserAccount = Depends(current_user),
    repository: InMemorySecretaryRepository = Depends(get_repository),
    subtype_code: str | None = Query(default=None),
):
    pricing_list = repository.list_tenant_pricing(user.company_id)
    # Index by activity code for fast lookup
    overrides = {p.activity_code: p for p in pricing_list}

    results = []
    for industry in request.app.state.catalogue.industries:
        for subtype in industry.subtypes:
            if subtype_code and subtype.code != subtype_code:
                continue
            for activity in subtype.activities:
                override = overrides.get(activity.code)
                results.append({
                    "id": _crc32_id(activity.code),
                    "template_id": _crc32_id(activity.code),
                    "code": activity.code,
                    "name": activity.name,
                    "subtype_code": activity.subtype_code,
                    "industry_code": activity.industry_code,
                    "selected_pricing_method_code": (
                        override.selected_pricing_method_code
                        if override else activity.default_pricing_method_code
                    ),
                    "default_pricing_method_code": activity.default_pricing_method_code,
                    "rate": override.rate if override else None,
                    "is_overridden": override is not None,
                })
    return results


@router.put("/tenant/{tenant_id}/{template_id}")
def upsert_tenant_activity_pricing(
    tenant_id: int,
    template_id: int,
    payload: dict,
    request: Request,
    user: UserAccount = Depends(current_user),
    repository: InMemorySecretaryRepository = Depends(get_repository),
):
    activity = _activity_by_id(request, template_id)
    if not activity:
        raise HTTPException(status_code=404, detail="Activity template not found")

    from secretary_clean.core.models import TenantActivityOverrideRequest
    override = TenantActivityOverrideRequest(
        selected_pricing_method_code=payload.get(
            "selected_pricing_method_code", activity.default_pricing_method_code
        ),
        rate=payload.get("rate"),
        enabled_additional_charge_codes=payload.get("enabled_additional_charge_codes", []),
    )
    saved = repository.save_tenant_pricing(user.company_id, activity.code, override)
    return {
        "id": template_id,
        "template_id": template_id,
        "code": activity.code,
        "selected_pricing_method_code": saved.selected_pricing_method_code,
        "rate": saved.rate,
        "is_overridden": True,
    }


@router.delete("/tenant/{tenant_id}/{template_id}")
def reset_tenant_activity_pricing(
    tenant_id: int,
    template_id: int,
    request: Request,
    user: UserAccount = Depends(current_user),
    repository: InMemorySecretaryRepository = Depends(get_repository),
):
    activity = _activity_by_id(request, template_id)
    if not activity:
        raise HTTPException(status_code=404, detail="Activity template not found")
    repository.reset_tenant_pricing(user.company_id, activity.code)
    return {"reset": True, "template_id": template_id}
