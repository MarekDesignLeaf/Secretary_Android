-- Migration 021: ui_control_registry entries for ActivityPricing screen
-- tenant_id = 0 means system-wide (available to all tenants)

INSERT INTO crm.ui_control_registry
    (tenant_id, control_code, screen_code, module_name, control_type,
     label, action_code, risk_level, voice_enabled, enabled, sort_order)
VALUES
    -- Navigate to activity pricing screen (global navigation command)
    (0, 'nav_activity_pricing', 'global', 'navigation', 'navigation',
     'Activity Pricing', 'navigate_to_activity_pricing', 'safe', true, true, 90),

    -- Activity pricing screen itself (screen-level control)
    (0, 'activity_pricing_open', 'settings', 'settings', 'navigation',
     'Open Activity Pricing', 'navigate_to_activity_pricing', 'safe', true, true, 80),

    -- Browse/list activities (within activity_pricing screen)
    (0, 'activity_pricing_browse', 'activity_pricing', 'activity_pricing', 'action',
     'Browse Activities', 'browse_activity_catalog', 'safe', true, true, 10),

    -- Save pricing override
    (0, 'activity_pricing_save', 'activity_pricing', 'activity_pricing', 'button',
     'Save Pricing', 'save_activity_pricing', 'safe', true, true, 20),

    -- Reset pricing to default
    (0, 'activity_pricing_reset', 'activity_pricing', 'activity_pricing', 'button',
     'Reset Pricing', 'reset_activity_pricing', 'moderate', true, true, 30)

ON CONFLICT (control_code) DO NOTHING;
