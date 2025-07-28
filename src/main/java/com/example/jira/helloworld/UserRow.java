package com.example.jira.helloworld;

public class UserRow {
    public final String username, displayName, lastLogin;

    public UserRow(String username, String displayName, String lastLogin) {
        this.username = username;
        this.displayName = displayName;
        this.lastLogin = lastLogin;
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
}
