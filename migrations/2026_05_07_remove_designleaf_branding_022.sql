-- Migration 022: Remove all DesignLeaf/landscaping hardcoding
-- Rename tenant, neutralize industry profile, clean up all company-specific data

-- 1. Rename tenant from DesignLeaf to Secretary (generic)
UPDATE tenants
SET name = 'Secretary', slug = 'secretary'
WHERE slug = 'designleaf';

-- 2. Update tenant_settings — remove DesignLeaf company name
UPDATE crm.tenant_settings
SET company_name = NULL,
    industry_description = NULL
WHERE company_name ILIKE '%designleaf%'
   OR company_name ILIKE '%design leaf%';

-- 3. Remove landscaping industry association from tenant operating profile
--    (tenant can reconfigure their own industry)
UPDATE tenant_industry_profile
SET is_primary = false, is_active = false
FROM tenants t
WHERE tenant_industry_profile.tenant_id = t.id
  AND t.slug = 'secretary'
  AND EXISTS (
      SELECT 1 FROM industry_subtypes ist
      WHERE ist.id = tenant_industry_profile.industry_subtype_id
        AND ist.code = 'landscaping'
  );

-- 4. Delete landscaping-specific job types / service types if any remain
--    (only for the 'landscaping' industry subtype links, not the global catalog)
DELETE FROM tenant_industry_profile
WHERE is_active = false AND is_primary = false
  AND tenant_id IN (SELECT id FROM tenants WHERE slug = 'secretary');

-- 5. Reset activation word if it was set to "hej designleaf" / "hey designleaf"
--    (stored in user prefs table if applicable — no-op if table doesn't exist)
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'user_preferences' AND table_schema = 'crm') THEN
    UPDATE crm.user_preferences
    SET value = 'hey secretary'
    WHERE key = 'activation_word'
      AND value ILIKE '%designleaf%';
  END IF;
END $$;

