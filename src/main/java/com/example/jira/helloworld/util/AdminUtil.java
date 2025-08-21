package com.example.jira.helloworld.util;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.user.ApplicationUser;

import java.util.Set;

public class AdminUtil {
    public static final Set<String> adminGroups = Set.of(
        "jira-administrators",
        "administrators",
        "system-administrators"
    );

    private static final GroupManager groupManager = ComponentAccessor.getComponent(GroupManager.class);

    public static boolean isUserAdmin(ApplicationUser user) {
        if(user == null)
            throw new IllegalArgumentException("User cannot be null");
        return groupManager.isUserInGroups(user, adminGroups);
    }
}
