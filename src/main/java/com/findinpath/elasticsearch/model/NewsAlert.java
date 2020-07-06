package com.findinpath.elasticsearch.model;

public class NewsAlert {
    private String id;
    private String name;
    private String email;
    private String query;

    public NewsAlert() {
    }

    public NewsAlert(String id, String name, String email, String query) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.query = query;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    @Override
    public String toString() {
        return "NewsAlert{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", query='" + query + '\'' +
                '}';
    }
}
