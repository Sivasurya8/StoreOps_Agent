package com.kiranapilot.agent.tools;

import com.kiranapilot.agent.ToolResult;
import com.kiranapilot.memory.OwnerPreferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PreferenceTools {

    private final OwnerPreferenceService preferenceService;

    /**
     * Tool: set_preference
     * Persist owner standing preferences across chat sessions ("always assume UPI", "default atta = Aashirvaad 5kg").
     */
    public ToolResult setPreference(Map<String, Object> args) {
        try {
            String key = (String) args.get("key");
            String value = (String) args.get("value");
            preferenceService.setPreference(key, value);
            return ToolResult.success(null, String.format("Saved preference: '%s' = '%s'. This will be remembered across all sessions.", key, value));
        } catch (Exception e) {
            return ToolResult.error("Failed to save preference: " + e.getMessage());
        }
    }

    /**
     * Tool: get_preference
     * Retrieve a specific stored preference.
     */
    public ToolResult getPreference(Map<String, Object> args) {
        try {
            String key = (String) args.get("key");
            String val = preferenceService.getPreference(key, null);
            if (val == null) {
                return ToolResult.success(null, "No preference set for '" + key + "'.");
            }
            return ToolResult.success(val, String.format("Preference '%s' is set to '%s'.", key, val));
        } catch (Exception e) {
            return ToolResult.error("Failed to get preference: " + e.getMessage());
        }
    }

    /**
     * Tool: get_all_preferences
     * List all standing owner preferences.
     */
    public ToolResult getAllPreferences(Map<String, Object> args) {
        try {
            Map<String, String> all = preferenceService.getAllPreferences();
            return ToolResult.success(all, "Owner Preferences: " + all.toString());
        } catch (Exception e) {
            return ToolResult.error("Failed to load preferences: " + e.getMessage());
        }
    }
}
