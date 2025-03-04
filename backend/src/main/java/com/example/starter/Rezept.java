package com.example.starter;
import java.util.*;
public class Rezept{
  private String id;
  private String title;
  private String description;
  private List<String> ingredients;
  private List<String> instructions;

  public Rezept(){
  }

  //constructor
  public Rezept(String id,String title,String description, List<String> ingredients,List<String> instructions){
    this.id=id;
    this.title=title;
    this.description=description;
    this.ingredients=ingredients;
    this.instructions=instructions;
  }

  //getters and setters
  public String getId(){
    return id;
  }

  public void setId(String id){
    this.id=id;
  }
  public String getTitle(){
    return title;
  }
  public void setTitle(String title){
    this.title=title;
  }

  public String getDescription(){
    return description;
  }

  public void setDescription(String description){
    this.description= description;
  }
  public  List<String> getIngredients(){
    return ingredients;
  }

  public void setIngredients(List<String> ingredients){
    this.ingredients=ingredients;
  }

  public List<String> getInstructions() {
    return instructions;
  }

  public void setInstructions(List<String> instructions) {
    this.instructions = instructions;
  }
}
