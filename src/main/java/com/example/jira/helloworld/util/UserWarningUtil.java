package com.example.jira.helloworld.util;

import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.bc.security.login.LoginService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.security.roles.ProjectRoleManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.web.bean.PagerFilter;
import com.example.jira.helloworld.ProjectUserResponse;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class UserWarningUtil {

    private static final GroupManager groupManager = ComponentAccessor.getComponent(GroupManager.class);
    private static final LoginService loginService = ComponentAccessor.getComponent(LoginService.class);
    private static final SearchService searchService = ComponentAccessor.getComponent(SearchService.class);
    private static final ProjectRoleManager projectRoleManager = ComponentAccessor.getComponent(ProjectRoleManager.class);

    public static List<String> getWarningsForUser(ApplicationUser applicationUser, ApplicationUser adminUser) throws Exception {
        Long lastLoginMillis = loginService.getLoginInfo(applicationUser.getUsername()).getLastLoginTime();


        List<String> warnings = new ArrayList<>();
        // never logged in
        if(lastLoginMillis == null) {
            warnings.add("Never logged in: This user has never logged in.");
            return warnings;
        }
        // no license
        if(!groupManager.isUserInGroup(applicationUser, groupManager.getGroup("jira-software-users"))) {
            warnings.add("No Jira license: This user is not in the jira-software-users group.");
            // inactive
            if(groupManager.getGroupsForUser(applicationUser).isEmpty())
                warnings.add("User is not in any group: This user is inactive and cannot log in or access anything.");
        }
        // admin has no license
        if (groupManager.isUserInGroup(applicationUser, groupManager.getGroup("jira-administrators"))
                && !groupManager.isUserInGroup(applicationUser, groupManager.getGroup("jira-software-users"))) {
            warnings.add("Admin but no Jira license: User is in administrators group but not in jira-software-users.");
        }

        // inactive user with issues assigned
        if(groupManager.getGroupNamesForUser(applicationUser).isEmpty()
                && countIssues("assignee = \"" + applicationUser.getUsername() + "\" AND statusCategory != Done",
                adminUser) > 0) {
            warnings.add("Inactive user has open issues assigned: Reassign these issues.");
        }


        // no log in in the last 90 days
        if(ChronoUnit.DAYS.between(Instant.ofEpochMilli(lastLoginMillis), Instant.now()) >= 90){
            // admin or not
            if(groupManager.isUserInGroup(applicationUser, groupManager.getGroup("jira-administrators")))
                warnings.add("Inactive admin: This user has admin rights and has not logged in for 90+ days.");
            else
                warnings.add("Inactive user: This user has not logged in for 90+ days.");
        }

        // no activity in the last 90 days
        int assigned = countIssues("assignee = \"" + applicationUser.getUsername() + "\" AND updated >= -90d", adminUser);
        int reported = countIssues("reporter = \"" + applicationUser.getUsername() + "\" AND created >= -90d", adminUser);
        int resolved = countIssues("assignee = \"" + applicationUser.getUsername() + "\" AND statusCategory = Done AND updated >= -90d", adminUser);
        if ((assigned + reported + resolved) == 0) {
            warnings.add("No activity: No issues assigned, resolved, or reported in 90 days.");
        }

        // no project participation in the last 90 days
        if(getProjectsForUserLastGivenDays(adminUser, applicationUser, 90).isEmpty()) {
            warnings.add("No project participation in last 90 days.");
        }





        return warnings;
    }


    public static int countIssues(String jql, ApplicationUser adminUser) throws Exception {
        SearchService.ParseResult parseResult = searchService.parseQuery(adminUser, jql);
        if (!parseResult.isValid()) {
            throw new RuntimeException("Invalid JQL query: " + jql);
        }
        SearchResults<?> searchResults = searchService.search(adminUser, parseResult.getQuery(), PagerFilter.getUnlimitedFilter());
        return searchResults.getTotal();

    }

    public static Set<ProjectUserResponse> getProjectsForUserLastGivenDays(ApplicationUser adminUser, ApplicationUser user, int days) throws SearchException {
        String jql = "(assignee = \"" + user.getUsername() + "\" OR reporter = \"" + user.getUsername() + "\") AND updated >= -" + days + "d";
        SearchService.ParseResult parseResult = searchService.parseQuery(adminUser, jql);
        if(parseResult.isValid()) {
            SearchResults<Issue> results = searchService.search(adminUser, parseResult.getQuery(), PagerFilter.getUnlimitedFilter());
            Set<ProjectUserResponse> projectResponses = new HashSet<>();
            for(Issue issue : results.getResults()) {
                ProjectUserResponse projectResponse = new ProjectUserResponse();
                Project project = issue.getProjectObject();
                projectResponse.setProjectKey(project.getKey());
                projectResponse.setProjectName(project.getName());
                projectResponse.setUserRoles(getUserRolesForProject(user, project));
                projectResponse.setUserName(user.getUsername());
                projectResponses.add(projectResponse);
//                System.out.println("Project: " + issue.getProjectObject().getKey() + ", Name: " + issue.getProjectObject().getName());
            }
            return projectResponses;
        }
        throw new SearchException("Invalid JQL query: " + jql);
    }

    public static List<String> getUserRolesForProject(ApplicationUser user, Project project) {
        List<String> roles = new ArrayList<>();
        if(project != null) {
            projectRoleManager.getProjectRoles(user, project).forEach(role -> roles.add(role.getName()));
            return roles;
        }
        else
            return Collections.emptyList();
    }
}

