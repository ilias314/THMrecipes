package com.example.starter;

public class Comments {
  private Integer id;
  private Integer userId;
  private Integer recipeId;
  private String content;
  private String createdAt;
  private String updatedAt;
  private String username; // For display purposes, not stored in DB

  // Getters and Setters
  public Integer getId() { return id; }
  public void setId(Integer id) { this.id = id; }

  public Integer getUserId() { return userId; }
  public void setUserId(Integer userId) { this.userId = userId; }

  public Integer getRecipeId() { return recipeId; }
  public void setRecipeId(Integer recipeId) { this.recipeId = recipeId; }

  public String getContent() { return content; }
  public void setContent(String content) { this.content = content; }

  public String getCreatedAt() { return createdAt; }
  public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

  public String getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

  public String getUsername() { return username; }
  public void setUsername(String username) { this.username = username; }
}
