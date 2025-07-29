package com.example.jira.helloworld;

import java.util.List;
import java.util.Objects;

public class ProjectUserResponse {
    private String projectKey;
    private String projectName;
    private List<String> userRoles;
    private String userName;

    public ProjectUserResponse() {
    }

    public String getProjectKey() {
        return projectKey;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public List<String> getUserRoles() {
        return userRoles;
    }

    public void setUserRoles(List<String> userRoles) {
        this.userRoles = userRoles;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProjectUserResponse that = (ProjectUserResponse) o;
        return Objects.equals(projectKey, that.projectKey); // Or include username if needed
    }
    @Override
    public int hashCode() {
        return Objects.hash(projectKey); // Or include username if needed
    }

}
