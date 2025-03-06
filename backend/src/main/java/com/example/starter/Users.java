package com.example.starter;

public class Users {
  private int id;
  private String username;
  private String email;
  private String password;

  // Konstruktor
  public Users(int id, String username, String email, String password) {
    this.id = id;
    this.username = username;
    this.email = email;
    this.password = password;
  }

  // Standard-Konstruktor
  public Users() {}

  // Getter und Setter
  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  @Override
  public String toString() {
    return "Users{" +
      "id=" + id +
      ", username='" + username + '\'' +
      ", email='" + email + '\'' +
      '}'; // Passwort wird absichtlich nicht ausgegeben
  }
}
