package com.example.starter;

public class Ratings {
  private Integer id;
  private Integer userId;
  private Integer recipeId;
  private Integer rating;
  private String createdAt;

  // Getters and Setters
  public Integer getId() { return id; }
  public void setId(Integer id) { this.id = id; }

  public Integer getUserId() { return userId; }
  public void setUserId(Integer userId) { this.userId = userId; }

  public Integer getRecipeId() { return recipeId; }
  public void setRecipeId(Integer recipeId) { this.recipeId = recipeId; }

  public Integer getRating() { return rating; }
  public void setRating(Integer rating) { this.rating = rating; }

  public String getCreatedAt() { return createdAt; }
  public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
