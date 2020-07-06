package com.findinpath.elasticsearch.model;

import java.time.Instant;
import java.util.Objects;

public class News {
    private String id;
    private String title;
    private String body;
    private String category;
    private Instant publishedDate;


    public News() {
    }

    public News(String id, String title, String body, String category, Instant publishedDate) {
        this.id = id;
        this.title = title;
        this.body = body;
        this.category = category;
        this.publishedDate = publishedDate;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Instant getPublishedDate() {
        return publishedDate;
    }

    public void setPublishedDate(Instant publishedDate) {
        this.publishedDate = publishedDate;
    }

    @Override
    public String toString() {
        return "News{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", body='" + body + '\'' +
                ", category='" + category + '\'' +
                ", publishedDate=" + publishedDate +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        News news = (News) o;
        return Objects.equals(id, news.id) &&
                Objects.equals(title, news.title) &&
                Objects.equals(body, news.body) &&
                Objects.equals(category, news.category) &&
                Objects.equals(publishedDate, news.publishedDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, body, category, publishedDate);
    }
}
