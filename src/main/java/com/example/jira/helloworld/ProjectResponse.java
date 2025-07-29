package com.example.jira.helloworld;

public class ProjectResponse {
    private String key;
    private String name;

    public ProjectResponse(){}

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
