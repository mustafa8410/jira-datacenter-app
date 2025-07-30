package com.example.jira.helloworld;

import java.util.List;

public class UserRow {
    public final String username, displayName, lastLogin;
    public final List<String> warnings;

    public UserRow(String username, String displayName, String lastLogin, List<String> warnings) {
        this.username = username;
        this.displayName = displayName;
        this.lastLogin = lastLogin;
        this.warnings = warnings;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getLastLogin() {
        return lastLogin;
    }

    public List<String> getWarnings() {
        return warnings;
    }
}
