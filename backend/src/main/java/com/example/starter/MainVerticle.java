package com.example.starter;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;
import org.mindrot.jbcrypt.BCrypt;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.handler.CorsHandler;

import java.util.ArrayList;
import java.util.List;

public class MainVerticle extends AbstractVerticle {
  private MySQLPool client;
  private JWTAuth jwtProvider;

  @Override
  public void start(Promise<Void> startPromise) {
    System.out.println("🚀 Server wird gestartet...");

    // 🔹 MariaDB-Verbindungsoptionen
    MySQLConnectOptions connectOptions = new MySQLConnectOptions()
      .setPort(3306)
      .setHost("ip1-dbs.mni.thm.de")  // Host anpassen
      .setDatabase("InfP-WS2425-04")   // Datenbankname
      .setUser("InfP-WS2425-04")              // Dein Benutzername
      .setPassword("ErJUVVyAzJzEaosu")   // Dein Passwort
      .setSslMode(io.vertx.mysqlclient.SslMode.DISABLED) // Falls SSL erforderlich
      .setTrustAll(true); // Falls der Server ein unsicheres Zertifikat hat

    PoolOptions poolOptions = new PoolOptions().setMaxSize(5);
    client = MySQLPool.pool(vertx, connectOptions, poolOptions);

    jwtProvider = JWTAuth.create(vertx, new JWTAuthOptions()
      .addPubSecKey(new PubSecKeyOptions()
        .setAlgorithm("HS256")
        .setBuffer("geheimes_jwt_schluessel")));

    // 📌 Verbindung testen
    client.getConnection(ar -> {
      if (ar.succeeded()) {
        System.out.println("✅ Erfolgreich mit MariaDB verbunden!");
      } else {
        System.err.println("❌ Verbindung zu MariaDB fehlgeschlagen: " + ar.cause().getMessage());
      }
    });

    // 📌 Router & CRUD-Routen
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());

    router.route().handler(CorsHandler.create("*")
      .allowedMethod(HttpMethod.GET)
      .allowedMethod(HttpMethod.POST)
      .allowedMethod(HttpMethod.PUT)
      .allowedMethod(HttpMethod.DELETE)
      .allowedHeader("Content-Type")
      .allowedHeader("Authorization"));

    router.route("/*").handler(StaticHandler.create("frontend"));

    router.route("/protected-route").handler(JWTAuthHandler.create(jwtProvider));

    // Auth-Routen
    router.post("/register").handler(this::register);
    router.post("/login").handler(this::login);
    router.post("/logout").handler(this::logout);

    // User routes
    router.post("/users").handler(this::createUser);
    router.get("/users").handler(this::getAllUsers);
    router.get("/users/:id").handler(this::getUserById);
    router.put("/users/:id").handler(this::updateUser);
    router.delete("/users/:id").handler(this::deleteUser);

    // Recipe routes
    JWTAuthHandler jwtAuthHandler = JWTAuthHandler.create(jwtProvider);
    router.route("/recipes*").handler(jwtAuthHandler);
    router.post("/recipes").handler(this::addRecipe);
    router.get("/recipes").handler(this::getAllRecipes);
    router.get("/recipes/:id").handler(this::getRecipesById);

    router.put("/recipes/:id").handler(this::updateRecipe);
    router.delete("/recipes/:id").handler(this::deleteRecipe);

    router.get("/users/:userId/recipes").handler(this::getRecipesByUserId);
    router.get("/recipe/search").handler(this::searchRecipes);

    // Add these routes to your start method

    router.route("/comments*").handler(jwtAuthHandler);
    router.route("/ratings*").handler(jwtAuthHandler);

// Rating routes
    router.post("/recipes/:recipeId/ratings").handler(this::addRating);
    router.get("/recipes/:recipeId/ratings").handler(this::getRecipeRatings);
    router.get("/ratings/:id").handler(this::getRatingById);
    router.put("/ratings/:id").handler(this::updateRating);
    router.delete("/ratings/:id").handler(this::deleteRating);
    router.get("/users/:userId/ratings").handler(this::getUserRatings);

// Comment routes
    router.post("/recipes/:recipeId/comments").handler(this::addComment);
    router.get("/recipes/:recipeId/comments").handler(this::getRecipeComments);
    router.get("/comments/:id").handler(this::getCommentById);
    router.put("/comments/:id").handler(this::updateComment);
    router.delete("/comments/:id").handler(this::deleteComment);
    router.get("/users/:userId/comments").handler(this::getUserComments);

    vertx.createHttpServer().requestHandler(router).listen(8080, http -> {
      if (http.succeeded()) {
        startPromise.complete();
        System.out.println("✅ HTTP-Server läuft auf Port 8080");
      } else {
        System.err.println("❌ Fehler beim Starten des Servers: " + http.cause().getMessage());
        startPromise.fail(http.cause());
      }
    });
  }

  // 📌 CREATE: Benutzer erstellen
  private void createUser(RoutingContext context) {
    JsonObject body = context.getBodyAsJson();

    // Überprüfe, ob alle erforderlichen Felder vorhanden sind
    if (body == null || !body.containsKey("username") ||
      !body.containsKey("email") || !body.containsKey("password")) {
      context.response()
        .setStatusCode(400)
        .end(new JsonObject().put("message", "Fehlende Daten: username, email und password sind erforderlich.").encode());
      return;
    }

    String username = body.getString("username");
    String email = body.getString("email");
    String password = body.getString("password");

    // Passwortvalidierung: Mindestens ein Großbuchstabe und zwei Ziffern
    if (!isValidPassword(password)) {
      context.response()
        .setStatusCode(400)
        .end(new JsonObject().put("message", "Das Passwort muss mindestens einen Großbuchstaben und mindestens zwei Ziffern enthalten.").encode());
      return;
    }

    // SQL zum Einfügen des Benutzers
    String sql = "INSERT INTO users (username, email, password) VALUES (?, ?, ?)";

    client.preparedQuery(sql).execute(Tuple.of(username, email, password), ar -> {
      if (ar.succeeded()) {
        context.response()
          .setStatusCode(201)
          .end(new JsonObject().put("message", "User erfolgreich erstellt.").encode());
      } else {
        System.err.println("❌ Fehler beim Einfügen in die DB: " + ar.cause().getMessage());
        context.response()
          .setStatusCode(500)
          .end(new JsonObject().put("message", "Fehler beim Erstellen des Benutzers: " + ar.cause().getMessage()).encode());
      }
    });
  }

  /**
   * Überprüft, ob das Passwort mindestens einen Großbuchstaben und zwei Ziffern enthält.
   */
  private boolean isValidPassword(String password) {
    return password.matches("^(?=.*[A-Z])(?=(.*\\d){2,}).{6,}$");
  }

  // 📌 READ: Alle Benutzer abrufen
  private void getAllUsers(RoutingContext context) {
    client.query("SELECT id, username, email FROM users").execute(ar -> {
      if (ar.succeeded()) {
        RowSet<Row> rows = ar.result();
        JsonArray users = new JsonArray();
        for (Row row : rows) {
          users.add(new JsonObject()
            .put("id", row.getInteger("id"))
            .put("username", row.getString("username"))
            .put("email", row.getString("email")));
        }
        context.response().putHeader("content-type", "application/json").end(users.encode());
      } else {
        System.err.println("❌ Fehler beim Abrufen von Benutzern: " + ar.cause().getMessage());
        context.response().setStatusCode(500).end("Fehler: " + ar.cause().getMessage());
      }
    });
  }

  // 📌 READ: Benutzer nach ID abrufen
  private void getUserById(RoutingContext context) {
    String id = context.pathParam("id");
    client.preparedQuery("SELECT id, username, email FROM users WHERE id = ?").execute(Tuple.of(Integer.parseInt(id)), ar -> {
      if (ar.succeeded() && ar.result().size() > 0) {
        context.response().putHeader("content-type", "application/json").end(ar.result().iterator().next().toJson().encode());
      } else {
        System.err.println("❌ Benutzer nicht gefunden: ID " + id);
        context.response().setStatusCode(404).end("Benutzer nicht gefunden");
      }
    });
  }

  // 📌 UPDATE: Benutzer aktualisieren
  private void updateUser(RoutingContext context) {
    String id = context.pathParam("id");

    // Check if the user is authenticated
    if (context.user() == null) {
      context.response().setStatusCode(401).end("Unauthorized: User not authenticated.");
      return;
    }

    // Safely retrieve the user ID from the principal
    JsonObject principal = context.user().principal();
    if (principal == null || !principal.containsKey("userId")) {
      context.response().setStatusCode(401).end("Unauthorized: User principal is invalid.");
      return;
    }

    Integer userIdFromToken = principal.getInteger("userId");

    // Check if user is authorized to perform the action
    if (!userIdFromToken.equals(Integer.parseInt(id))) {
      context.response().setStatusCode(403).end("Forbidden: You can only update your own profile.");
      return;
    }

    // Validate request body
    JsonObject body = context.getBodyAsJson();
    if (body == null || !body.containsKey("username") || !body.containsKey("email") || !body.containsKey("password")) {
      context.response().setStatusCode(400).end("❌ Fehlende Daten");
      return;
    }

    // Validate email format
    if (!isValidEmail(body.getString("email"))) {
      context.response().setStatusCode(400).end("Invalid email format.");
      return;
    }

    // Validate password strength
    if (!isValidPassword(body.getString("password"))) {
      context.response().setStatusCode(400).end("Password must contain at least one uppercase letter and two digits.");
      return;
    }

    // Execute the database query
    String sql = "UPDATE users SET username = ?, email = ?, password = ? WHERE id = ?";
    client.preparedQuery(sql).execute(Tuple.of(body.getString("username"), body.getString("email"), body.getString("password"), Integer.parseInt(id)), ar -> {
      if (ar.succeeded() && ar.result().rowCount() > 0) {
        context.response().end("✅ Benutzer aktualisiert");
      } else {
        System.err.println("❌ Fehler beim Aktualisieren des Benutzers: ID " + id);
        context.response().setStatusCode(404).end("Benutzer nicht gefunden oder keine Änderung");
      }
    });
  }

  private boolean isValidEmail(String email) {
    return email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
  }

  // 📌 DELETE: Benutzer löschen
  private void deleteUser(RoutingContext context) {
    String id = context.pathParam("id");
    Integer userIdFromToken = context.user().principal().getInteger("userId");

    // Check if the logged-in user is trying to delete their own account
    if (!userIdFromToken.equals(Integer.parseInt(id))) {
      context.response().setStatusCode(403).end("Forbidden: You can only delete your own account.");
      return;
    }

    // SQL queries to delete recipes and user
    String deleteRecipesQuery = "DELETE FROM recipes WHERE user_id = ?";
    String deleteUserQuery = "DELETE FROM users WHERE id = ?";

    // Get a connection from the pool
    client.getConnection(conn -> {
      if (conn.succeeded()) {
        SqlConnection connection = conn.result();

        // Begin a transaction
        connection.begin(tx -> {
          if (tx.succeeded()) {
            // First, delete all recipes associated with the user
            connection.preparedQuery(deleteRecipesQuery).execute(Tuple.of(Integer.parseInt(id)), ar -> {
              if (ar.succeeded()) {
              if (ar.succeeded())
                // If recipes are deleted successfully, delete the user
                connection.preparedQuery(deleteUserQuery).execute(Tuple.of(Integer.parseInt(id)), ar2 -> {
                  if (ar2.succeeded()) {
                    // Commit the transaction if both deletions succeed
                    tx.result().commit(commit -> {
                      if (commit.succeeded()) {
                        context.response().setStatusCode(204).end(); // 204 No Content
                      } else {
                        // Rollback if commit fails
                        tx.result().rollback();
                        context.response().setStatusCode(500).end("Failed to commit transaction.");
                      }
                      connection.close(); // Always close the connection
                    });
                  } else {
                    // Rollback if user deletion fails
                    tx.result().rollback();
                    context.response().setStatusCode(500).end("Failed to delete user.");
                    connection.close(); // Always close the connection
                  }
                });
              } else {
                // Rollback if recipe deletion fails
                tx.result().rollback();
                context.response().setStatusCode(500).end("Failed to delete associated recipes.");
                connection.close(); // Always close the connection
              }
            });
          } else {
            // Handle transaction begin failure
            context.response().setStatusCode(500).end("Failed to begin transaction.");
            connection.close(); // Always close the connection
          }
        });
      } else {
        // Handle connection failure
        context.response().setStatusCode(500).end("Failed to get database connection.");
      }
    });
  }

  // ✅ Benutzer registrieren
  private void register(RoutingContext context) {
    JsonObject body = context.getBodyAsJson();
    if (!isValidPassword(body.getString("password"))) {
      context.response().setStatusCode(400).end("Passwort muss mindestens einen Großbuchstaben und zwei Ziffern enthalten.");
      return;
    }

    String hashedPassword = BCrypt.hashpw(body.getString("password"), BCrypt.gensalt());
    String sql = "INSERT INTO users (username, email, password) VALUES (?, ?, ?)";

    client.preparedQuery(sql).execute(Tuple.of(body.getString("username"), body.getString("email"), hashedPassword), ar -> {
      if (ar.succeeded()) {
        context.response().setStatusCode(201).end("Benutzer erfolgreich registriert.");
      } else {
        context.response().setStatusCode(500).end("Fehler bei der Registrierung.");
      }
    });
  }

  // ✅ Benutzer-Login
  private void login(RoutingContext context) {
    JsonObject body = context.getBodyAsJson();
    String sql = "SELECT id, password FROM users WHERE email = ?";

    client.preparedQuery(sql).execute(Tuple.of(body.getString("email")), ar -> {
      if (ar.succeeded() && ar.result().size() > 0) {
        Row row = ar.result().iterator().next();
        String storedPassword = row.getString("password");

        if (BCrypt.checkpw(body.getString("password"), storedPassword)) {
          String token = jwtProvider.generateToken(new JsonObject().put("userId", row.getInteger("id")),
            new JWTOptions().setExpiresInMinutes(60));

          context.response()
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("token", token).put("userId", row.getInteger("id")).encode());
        } else {
          context.response().setStatusCode(401).end("{\"message\": \"Falsche Zugangsdaten\"}");
        }
      } else {
        context.response().setStatusCode(401).end("{\"message\": \"Benutzer nicht gefunden\"}");
      }
    });
  }


  // ✅ Benutzer-Logout (nur Token löschen)
  private void logout(RoutingContext context) {
    context.response().setStatusCode(200).end("Erfolgreich abgemeldet.");
  }

  // Recipe handlers
  private void addRecipe(RoutingContext routingContext) {
    JsonObject body = routingContext.getBodyAsJson();
    if (body == null || !body.containsKey("title") || !body.containsKey("ingredients") || !body.containsKey("instructions")) {
      routingContext.response()
        .setStatusCode(400)
        .end(new JsonObject().put("message", "Missing required fields: title, ingredients, instructions.").encode());
      return;
    }

    Rezept rezept = body.mapTo(Rezept.class);
    System.out.println("Adding recipe: " + rezept.getTitle());

    client.getConnection(conn -> {
      if (conn.succeeded()) {
        SqlConnection connection = conn.result();
        connection.preparedQuery("INSERT INTO recipes (user_id, title, description, ingredients, instructions) VALUES (?, ?, ?, ?, ?)")
          .execute(Tuple.of(rezept.getUserId(), rezept.getTitle(), rezept.getDescription(), Json.encode(rezept.getIngredients()), Json.encode(rezept.getInstructions())), ar -> {
            connection.close();
            if (ar.succeeded()) {
              routingContext.response()
                .setStatusCode(201)
                .putHeader("content-type", "application/json")
                .end(Json.encodePrettily(rezept));
            } else {
              System.err.println("❌ Error inserting recipe: " + ar.cause().getMessage());
              routingContext.response()
                .setStatusCode(500)
                .end(new JsonObject().put("message", "Failed to insert recipe: " + ar.cause().getMessage()).encode());
            }
          });
      } else {
        System.err.println("❌ Failed to connect to database: " + conn.cause().getMessage());
        routingContext.response()
          .setStatusCode(500)
          .end(new JsonObject().put("message", "Failed to connect to database: " + conn.cause().getMessage()).encode());
      }
    });
  }


  private void getAllRecipes(RoutingContext routingContext) {
    client.getConnection(conn -> {
      if (conn.succeeded()) {
        SqlConnection connection = conn.result();
        connection.query("SELECT * FROM recipes").execute(ar -> {
          connection.close();
          if (ar.succeeded()) {
            List<Rezept> recipes = new ArrayList<>();
            ar.result().forEach(row -> {
              Rezept rezept = new Rezept();
              rezept.setId(row.getInteger("id"));
              rezept.setUserId(row.getInteger("user_id"));
              rezept.setTitle(row.getString("title"));
              rezept.setDescription(row.getString("description"));
              rezept.setIngredients(Json.decodeValue(row.getString("ingredients"), List.class));
              rezept.setInstructions(Json.decodeValue(row.getString("instructions"), List.class));
              rezept.setCreatedAt(row.getLocalDateTime("created_at").toString());
              recipes.add(rezept);
            });
            routingContext.response()
              .setStatusCode(200)
              .putHeader("content-type", "application/json")
              .end(Json.encodePrettily(recipes));
          } else {
            routingContext.fail(500);
          }
        });
      } else {
        routingContext.fail(conn.cause());
      }
    });
  }

  private void getRecipesById(RoutingContext routingContext) {
    String id = routingContext.request().getParam("id");

    try {
      int recipeId = Integer.parseInt(id);
    } catch (NumberFormatException e) {
      routingContext.response()
        .setStatusCode(400)
        .end(new JsonObject().put("message", "Invalid recipe ID: must be a number").encode());
      return;
    }

    client.getConnection(conn -> {
      if (conn.succeeded()) {
        SqlConnection connection = conn.result();
        connection.preparedQuery("SELECT * FROM recipes WHERE id = ?")
          .execute(Tuple.of(Integer.parseInt(id)), ar -> {
            connection.close();
            if (ar.succeeded() && ar.result().size() > 0) {
              Rezept rezept = new Rezept();
              rezept.setId(ar.result().iterator().next().getInteger("id"));
              rezept.setUserId(ar.result().iterator().next().getInteger("user_id"));
              rezept.setTitle(ar.result().iterator().next().getString("title"));
              rezept.setDescription(ar.result().iterator().next().getString("description"));
              rezept.setIngredients(Json.decodeValue(ar.result().iterator().next().getString("ingredients"), List.class));
              rezept.setInstructions(Json.decodeValue(ar.result().iterator().next().getString("instructions"), List.class));
              rezept.setCreatedAt(ar.result().iterator().next().getLocalDateTime("created_at").toString());
              routingContext.response()
                .setStatusCode(200)
                .putHeader("content-type", "application/json")
                .end(Json.encodePrettily(rezept));
            } else {
              routingContext.response().setStatusCode(404).end();
            }
          });
      } else {
        routingContext.fail(conn.cause());
      }
    });
  }

  // Handler for updating a recipe
  private void updateRecipe(RoutingContext routingContext) {
    String recipeId = routingContext.pathParam("id");
    JsonObject body = routingContext.body().asJsonObject();
    Integer userIdFromToken = routingContext.user().principal().getInteger("userId");

    // Check if the recipe exists and belongs to the user
    String checkOwnershipQuery = "SELECT user_id FROM recipes WHERE id = ?";
    client.preparedQuery(checkOwnershipQuery).execute(Tuple.of(Integer.parseInt(recipeId)), checkResult -> {
      if (checkResult.succeeded() && checkResult.result().size() > 0) {
        int ownerId = checkResult.result().iterator().next().getInteger("user_id");

        if (ownerId != userIdFromToken) {
          routingContext.response().setStatusCode(403).end("Forbidden: You can only modify your own recipes.");
          return;
        }

        // Update the recipe
        String updateQuery = "UPDATE recipes SET title = ?, description = ?, ingredients = ?, instructions = ? WHERE id = ?";
        Tuple params = Tuple.of(
          body.getString("title", ""), // Default to empty string if not provided
          body.getString("description", ""), // Default to empty string if not provided
          Json.encode(body.getJsonArray("ingredients", new JsonArray())), // Default to empty JsonArray if not provided
          Json.encode(body.getJsonArray("instructions", new JsonArray())), // Default to empty JsonArray if not provided
          Integer.parseInt(recipeId)
        );

        client.preparedQuery(updateQuery).execute(params, updateResult -> {
          if (updateResult.succeeded()) {
            routingContext.response().setStatusCode(200).end("Recipe updated");
          } else {
            System.err.println("❌ Error updating recipe: " + updateResult.cause().getMessage());
            routingContext.response().setStatusCode(500).end("Failed to update recipe");
          }
        });
      } else {
        routingContext.response().setStatusCode(404).end("Recipe not found");
      }
    });
  }


  // Handler for deleting a recipe
  private void deleteRecipe(RoutingContext routingContext) {
    String recipeId = routingContext.pathParam("id");
    Integer userIdFromToken = routingContext.user().principal().getInteger("userId");

    String checkOwnershipQuery = "SELECT user_id FROM recipes WHERE id = ?";
    client.preparedQuery(checkOwnershipQuery).execute(Tuple.of(Integer.parseInt(recipeId)), checkResult -> {
      if (checkResult.succeeded() && checkResult.result().size() > 0) {
        int ownerId = checkResult.result().iterator().next().getInteger("user_id");

        if (ownerId != userIdFromToken) {
          routingContext.response().setStatusCode(403).end("Forbidden: You can only delete your own recipes.");
          return;
        }

        String deleteQuery = "DELETE FROM recipes WHERE id = ?";
        client.preparedQuery(deleteQuery).execute(Tuple.of(Integer.parseInt(recipeId)), deleteResult -> {
          if (deleteResult.succeeded()) {
            routingContext.response().setStatusCode(200).end("Recipe deleted");
          } else {
            routingContext.response().setStatusCode(500).end("Failed to delete recipe");
          }
        });
      } else {
        routingContext.response().setStatusCode(404).end("Recipe not found");
      }
    });
  }

  private void getRecipesByUserId(RoutingContext routingContext) {
    String userId = routingContext.request().getParam("userId");

    // Validate the user ID
    if (userId == null || userId.isEmpty()) {
      routingContext.response()
        .setStatusCode(400)
        .end(new JsonObject().put("message", "User ID is required").encode());
      return;
    }

    // Query the database for recipes associated with the user ID
    client.getConnection(conn -> {
      if (conn.succeeded()) {
        SqlConnection connection = conn.result();
        connection.preparedQuery("SELECT * FROM recipes WHERE user_id = ?")
          .execute(Tuple.of(Integer.parseInt(userId)), ar -> {
            connection.close(); // Always close the connection
            if (ar.succeeded()) {
              RowSet<Row> rows = ar.result();
              List<Rezept> recipes = new ArrayList<>();

              // Map rows to Rezept objects
              for (Row row : rows) {
                Rezept rezept = new Rezept();
                rezept.setId(row.getInteger("id"));
                rezept.setUserId(row.getInteger("user_id"));
                rezept.setTitle(row.getString("title"));
                rezept.setDescription(row.getString("description"));
                rezept.setIngredients(Json.decodeValue(row.getString("ingredients"), List.class));
                rezept.setInstructions(Json.decodeValue(row.getString("instructions"), List.class));
                rezept.setCreatedAt(row.getLocalDateTime("created_at").toString());
                recipes.add(rezept);
              }

              // Return the recipes as JSON
              routingContext.response()
                .setStatusCode(200)
                .putHeader("content-type", "application/json")
                .end(Json.encodePrettily(recipes));
            } else {
              routingContext.fail(500); // Internal server error
            }
          });
      } else {
        routingContext.fail(conn.cause()); // Connection failed
      }
    });
  }
  private void searchRecipes(RoutingContext context) {
    // Get the search query from the request
    List<String> queryParams = context.queryParam("q");
    if (queryParams.isEmpty()) {
      context.response()
        .setStatusCode(400)
        .end(new JsonObject().put("message", "Search query parameter 'q' is required").encode());
      return;
    }

    String query = queryParams.get(0).trim();
    if (query.isEmpty()) {
      context.response()
        .setStatusCode(400)
        .end(new JsonObject().put("message", "Search query cannot be empty").encode());
      return;
    }

    // SQL query to search for recipes by title or ingredients
    String sql = "SELECT * FROM recipes WHERE title LIKE ? OR ingredients LIKE ?";

    // Use the MySQL client to execute the query
    client.preparedQuery(sql).execute(Tuple.of("%" + query + "%", "%" + query + "%"), ar -> {
      if (ar.succeeded()) {
        RowSet<Row> rows = ar.result();
        List<Rezept> recipes = new ArrayList<>();

        // Map rows to Rezept objects
        for (Row row : rows) {
          Rezept rezept = new Rezept();
          rezept.setId(row.getInteger("id"));
          rezept.setUserId(row.getInteger("user_id"));
          rezept.setTitle(row.getString("title"));
          rezept.setDescription(row.getString("description"));
          rezept.setIngredients(Json.decodeValue(row.getString("ingredients"), List.class));
          rezept.setInstructions(Json.decodeValue(row.getString("instructions"), List.class));
          rezept.setCreatedAt(row.getLocalDateTime("created_at").toString());
          recipes.add(rezept);
        }

        // Return the recipes as JSON
        context.response()
          .setStatusCode(200)
          .putHeader("content-type", "application/json")
          .end(Json.encodePrettily(recipes));
      } else {
        // Handle database errors
        System.err.println("❌ Error searching recipes: " + ar.cause().getMessage());
        context.response()
          .setStatusCode(500)
          .end(new JsonObject().put("message", "Failed to search recipes: " + ar.cause().getMessage()).encode());
      }
    });
  }
  private void addRating(RoutingContext context) {
    // Get authenticated user ID
    Integer userId = context.user().principal().getInteger("userId");
    String recipeId = context.pathParam("recipeId");
    JsonObject body = context.getBodyAsJson();

    // Validate input
    if (body == null || !body.containsKey("rating")) {
      context.response()
        .setStatusCode(400)
        .end(new JsonObject().put("message", "Rating value is required").encode());
      return;
    }

    Integer ratingValue = body.getInteger("rating");
    // Validate rating value (1-5)
    if (ratingValue == null || ratingValue < 1 || ratingValue > 5) {
      context.response()
        .setStatusCode(400)
        .end(new JsonObject().put("message", "Rating must be between 1 and 5").encode());
      return;
    }

    // Check if recipe exists
    client.preparedQuery("SELECT id FROM recipes WHERE id = ?")
      .execute(Tuple.of(Integer.parseInt(recipeId)), recipeCheck -> {
        if (recipeCheck.succeeded() && recipeCheck.result().size() > 0) {
          // Check if user has already rated this recipe
          client.preparedQuery("SELECT id FROM ratings WHERE user_id = ? AND recipe_id = ?")
            .execute(Tuple.of(userId, Integer.parseInt(recipeId)), existingRating -> {
              if (existingRating.succeeded() && existingRating.result().size() > 0) {
                // User has already rated this recipe
                context.response()
                  .setStatusCode(409)
                  .end(new JsonObject().put("message", "You have already rated this recipe. Use PUT to update.").encode());
              } else {
                // Insert new rating
                client.preparedQuery("INSERT INTO ratings (user_id, recipe_id, rating) VALUES (?, ?, ?)")
                  .execute(Tuple.of(userId, Integer.parseInt(recipeId), ratingValue), ar -> {
                    if (ar.succeeded()) {
                      // Get the new rating ID
                      client.preparedQuery("SELECT LAST_INSERT_ID() as id")
                        .execute(idResult -> {
                          if (idResult.succeeded() && idResult.result().size() > 0) {
                            Integer newId = idResult.result().iterator().next().getInteger("id");
                            Ratings rating = new Ratings();
                            rating.setId(newId);
                            rating.setUserId(userId);
                            rating.setRecipeId(Integer.parseInt(recipeId));
                            rating.setRating(ratingValue);

                            context.response()
                              .setStatusCode(201)
                              .putHeader("content-type", "application/json")
                              .end(Json.encodePrettily(rating));
                          } else {
                            context.response()
                              .setStatusCode(500)
                              .end(new JsonObject().put("message", "Failed to retrieve new rating ID").encode());
                          }
                        });
                    } else {
                      context.response()
                        .setStatusCode(500)
                        .end(new JsonObject().put("message", "Failed to add rating: " + ar.cause().getMessage()).encode());
                    }
                  });
              }
            });
        } else {
          context.response()
            .setStatusCode(404)
            .end(new JsonObject().put("message", "Recipe not found").encode());
        }
      });
  }
  private void getRecipeRatings(RoutingContext context) {
    String recipeId = context.pathParam("recipeId");

    // Check if recipe exists
    client.preparedQuery("SELECT id FROM recipes WHERE id = ?")
      .execute(Tuple.of(Integer.parseInt(recipeId)), recipeCheck -> {
        if (recipeCheck.succeeded() && recipeCheck.result().size() > 0) {
          client.preparedQuery("SELECT r.*, u.username FROM ratings r JOIN users u ON r.user_id = u.id WHERE r.recipe_id = ?")
            .execute(Tuple.of(Integer.parseInt(recipeId)), ar -> {
              if (ar.succeeded()) {
                List<JsonObject> ratings = new ArrayList<>();
                ar.result().forEach(row -> {
                  JsonObject rating = new JsonObject()
                    .put("id", row.getInteger("id"))
                    .put("userId", row.getInteger("user_id"))
                    .put("recipeId", row.getInteger("recipe_id"))
                    .put("rating", row.getInteger("rating"))
                    .put("createdAt", row.getLocalDateTime("created_at").toString())
                    .put("username", row.getString("username"));
                  ratings.add(rating);
                });

                // Calculate average rating
                double averageRating = ratings.stream()
                  .mapToInt(r -> r.getInteger("rating"))
                  .average()
                  .orElse(0.0);

                JsonObject response = new JsonObject()
                  .put("ratings", new JsonArray(ratings))
                  .put("averageRating", averageRating)
                  .put("count", ratings.size());

                context.response()
                  .putHeader("content-type", "application/json")
                  .end(response.encode());
              } else {
                context.response()
                  .setStatusCode(500)
                  .end(new JsonObject().put("message", "Failed to get ratings: " + ar.cause().getMessage()).encode());
              }
            });
        } else {
          context.response()
            .setStatusCode(404)
            .end(new JsonObject().put("message", "Recipe not found").encode());
        }
      });
  }
  private void getRatingById(RoutingContext context) {
    String id = context.pathParam("id");

    client.preparedQuery("SELECT r.*, u.username FROM ratings r JOIN users u ON r.user_id = u.id WHERE r.id = ?")
      .execute(Tuple.of(Integer.parseInt(id)), ar -> {
        if (ar.succeeded() && ar.result().size() > 0) {
          Row row = ar.result().iterator().next();
          JsonObject rating = new JsonObject()
            .put("id", row.getInteger("id"))
            .put("userId", row.getInteger("user_id"))
            .put("recipeId", row.getInteger("recipe_id"))
            .put("rating", row.getInteger("rating"))
            .put("createdAt", row.getLocalDateTime("created_at").toString())
            .put("username", row.getString("username"));

          context.response()
            .putHeader("content-type", "application/json")
            .end(rating.encode());
        } else {
          context.response()
            .setStatusCode(404)
            .end(new JsonObject().put("message", "Rating not found").encode());
        }
      });
  }
  private void updateRating(RoutingContext context) {
    String id = context.pathParam("id");
    Integer userId = context.user().principal().getInteger("userId");
    JsonObject body = context.getBodyAsJson();

    if (body == null || !body.containsKey("rating")) {
      context.response()
        .setStatusCode(400)
        .end(new JsonObject().put("message", "Rating value is required").encode());
      return;
    }

    Integer ratingValue = body.getInteger("rating");
    if (ratingValue == null || ratingValue < 1 || ratingValue > 5) {
      context.response()
        .setStatusCode(400)
        .end(new JsonObject().put("message", "Rating must be between 1 and 5").encode());
      return;
    }

    // Check if rating exists and user is the owner
    client.preparedQuery("SELECT user_id FROM ratings WHERE id = ?")
      .execute(Tuple.of(Integer.parseInt(id)), ar -> {
        if (ar.succeeded() && ar.result().size() > 0) {
          Integer ratingUserId = ar.result().iterator().next().getInteger("user_id");

          if (!userId.equals(ratingUserId)) {
            context.response()
              .setStatusCode(403)
              .end(new JsonObject().put("message", "You can only update your own ratings").encode());
            return;
          }

          // Update the rating
          client.preparedQuery("UPDATE ratings SET rating = ? WHERE id = ?")
            .execute(Tuple.of(ratingValue, Integer.parseInt(id)), updateResult -> {
              if (updateResult.succeeded()) {
                client.preparedQuery("SELECT r.*, u.username FROM ratings r JOIN users u ON r.user_id = u.id WHERE r.id = ?")
                  .execute(Tuple.of(Integer.parseInt(id)), getResult -> {
                    if (getResult.succeeded() && getResult.result().size() > 0) {
                      Row row = getResult.result().iterator().next();
                      JsonObject rating = new JsonObject()
                        .put("id", row.getInteger("id"))
                        .put("userId", row.getInteger("user_id"))
                        .put("recipeId", row.getInteger("recipe_id"))
                        .put("rating", row.getInteger("rating"))
                        .put("createdAt", row.getLocalDateTime("created_at").toString())
                        .put("username", row.getString("username"));

                      context.response()
                        .putHeader("content-type", "application/json")
                        .end(rating.encode());
                    } else {
                      context.response()
                        .setStatusCode(500)
                        .end(new JsonObject().put("message", "Failed to retrieve updated rating").encode());
                    }
                  });
              } else {
                context.response()
                  .setStatusCode(500)
                  .end(new JsonObject().put("message", "Failed to update rating: " + updateResult.cause().getMessage()).encode());
              }
            });
        } else {
          context.response()
            .setStatusCode(404)
            .end(new JsonObject().put("message", "Rating not found").encode());
        }
      });
  }
  private void deleteRating(RoutingContext context) {
    String id = context.pathParam("id");
    Integer userId = context.user().principal().getInteger("userId");

    // Check if rating exists and user is the owner
    client.preparedQuery("SELECT user_id FROM ratings WHERE id = ?")
      .execute(Tuple.of(Integer.parseInt(id)), ar -> {
        if (ar.succeeded() && ar.result().size() > 0) {
          Integer ratingUserId = ar.result().iterator().next().getInteger("user_id");

          if (!userId.equals(ratingUserId)) {
            context.response()
              .setStatusCode(403)
              .end(new JsonObject().put("message", "You can only delete your own ratings").encode());
            return;
          }

          // Delete the rating
          client.preparedQuery("DELETE FROM ratings WHERE id = ?")
            .execute(Tuple.of(Integer.parseInt(id)), deleteResult -> {
              if (deleteResult.succeeded()) {
                context.response()
                  .setStatusCode(200)
                  .end(new JsonObject().put("message", "Rating deleted successfully").encode());
              } else {
                context.response()
                  .setStatusCode(500)
                  .end(new JsonObject().put("message", "Failed to delete rating: " + deleteResult.cause().getMessage()).encode());
              }
            });
        } else {
          context.response()
            .setStatusCode(404)
            .end(new JsonObject().put("message", "Rating not found").encode());
        }
      });
  }
  private void getUserRatings(RoutingContext context) {
    String userId = context.pathParam("userId");

    // Check if user exists
    client.preparedQuery("SELECT id FROM users WHERE id = ?")
      .execute(Tuple.of(Integer.parseInt(userId)), userCheck -> {
        if (userCheck.succeeded() && userCheck.result().size() > 0) {
          client.preparedQuery("SELECT r.*, rec.title as recipe_title FROM ratings r JOIN recipes rec ON r.recipe_id = rec.id WHERE r.user_id = ?")
            .execute(Tuple.of(Integer.parseInt(userId)), ar -> {
              if (ar.succeeded()) {
                List<JsonObject> ratings = new ArrayList<>();
                ar.result().forEach(row -> {
                  JsonObject rating = new JsonObject()
                    .put("id", row.getInteger("id"))
                    .put("userId", row.getInteger("user_id"))
                    .put("recipeId", row.getInteger("recipe_id"))
                    .put("recipeTitle", row.getString("recipe_title"))
                    .put("rating", row.getInteger("rating"))
                    .put("createdAt", row.getLocalDateTime("created_at").toString());
                  ratings.add(rating);
                });

                context.response()
                  .putHeader("content-type", "application/json")
                  .end(new JsonArray(ratings).encode());
              } else {
                context.response()
                  .setStatusCode(500)
                  .end(new JsonObject().put("message", "Failed to get user ratings: " + ar.cause().getMessage()).encode());
              }
            });
        } else {
          context.response()
            .setStatusCode(404)
            .end(new JsonObject().put("message", "User not found").encode());
        }
      });
  }
  private void addComment(RoutingContext context) {
    Integer userId = context.user().principal().getInteger("userId");
    String recipeId = context.pathParam("recipeId");
    JsonObject body = context.getBodyAsJson();

    // Validate input
    if (body == null || !body.containsKey("content")) {
      context.response()
        .setStatusCode(400)
        .end(new JsonObject().put("message", "Comment content is required").encode());
      return;
    }

    String content = body.getString("content");
    if (content == null || content.trim().isEmpty()) {
      context.response()
        .setStatusCode(400)
        .end(new JsonObject().put("message", "Comment content cannot be empty").encode());
      return;
    }

    // Check if recipe exists
    client.preparedQuery("SELECT id FROM recipes WHERE id = ?")
      .execute(Tuple.of(Integer.parseInt(recipeId)), recipeCheck -> {
        if (recipeCheck.succeeded() && recipeCheck.result().size() > 0) {
          // Insert new comment
          client.preparedQuery("INSERT INTO comments (user_id, recipe_id, content) VALUES (?, ?, ?)")
            .execute(Tuple.of(userId, Integer.parseInt(recipeId), content), ar -> {
              if (ar.succeeded()) {
                // Get the new comment ID
                client.preparedQuery("SELECT LAST_INSERT_ID() as id")
                  .execute(idResult -> {
                    if (idResult.succeeded() && idResult.result().size() > 0) {
                      Integer newId = idResult.result().iterator().next().getInteger("id");

                      // Get the username for display
                      client.preparedQuery("SELECT username FROM users WHERE id = ?")
                        .execute(Tuple.of(userId), userResult -> {
                          if (userResult.succeeded() && userResult.result().size() > 0) {
                            String username = userResult.result().iterator().next().getString("username");

                            Comments comment = new Comments();
                            comment.setId(newId);
                            comment.setUserId(userId);
                            comment.setRecipeId(Integer.parseInt(recipeId));
                            comment.setContent(content);
                            comment.setUsername(username);

                            context.response()
                              .setStatusCode(201)
                              .putHeader("content-type", "application/json")
                              .end(Json.encodePrettily(comment));
                          } else {
                            context.response()
                              .setStatusCode(500)
                              .end(new JsonObject().put("message", "Failed to retrieve username").encode());
                          }
                        });
                    } else {
                      context.response()
                        .setStatusCode(500)
                        .end(new JsonObject().put("message", "Failed to retrieve new comment ID").encode());
                    }
                  });
              } else {
                context.response()
                  .setStatusCode(500)
                  .end(new JsonObject().put("message", "Failed to add comment: " + ar.cause().getMessage()).encode());
              }
            });
        } else {
          context.response()
            .setStatusCode(404)
            .end(new JsonObject().put("message", "Recipe not found").encode());
        }
      });
  }
  private void getRecipeComments(RoutingContext context) {
    String recipeId = context.pathParam("recipeId");

    // Check if recipe exists
    client.preparedQuery("SELECT id FROM recipes WHERE id = ?")
      .execute(Tuple.of(Integer.parseInt(recipeId)), recipeCheck -> {
        if (recipeCheck.succeeded() && recipeCheck.result().size() > 0) {
          client.preparedQuery("SELECT c.*, u.username FROM comments c JOIN users u ON c.user_id = u.id WHERE c.recipe_id = ? ORDER BY c.created_at DESC")
            .execute(Tuple.of(Integer.parseInt(recipeId)), ar -> {
              if (ar.succeeded()) {
                List<JsonObject> comments = new ArrayList<>();
                ar.result().forEach(row -> {
                  JsonObject comment = new JsonObject()
                    .put("id", row.getInteger("id"))
                    .put("userId", row.getInteger("user_id"))
                    .put("recipeId", row.getInteger("recipe_id"))
                    .put("content", row.getString("content"))
                    .put("createdAt", row.getLocalDateTime("created_at").toString())
                    .put("updatedAt", row.getLocalDateTime("updated_at").toString())
                    .put("username", row.getString("username"));
                  comments.add(comment);
                });

                context.response()
                  .putHeader("content-type", "application/json")
                  .end(new JsonArray(comments).encode());
              } else {
                context.response()
                  .setStatusCode(500)
                  .end(new JsonObject().put("message", "Failed to get comments: " + ar.cause().getMessage()).encode());
              }
            });
        } else {
          context.response()
            .setStatusCode(404)
            .end(new JsonObject().put("message", "Recipe not found").encode());
        }
      });
  }
  private void getCommentById(RoutingContext context) {
    String id = context.pathParam("id");

    client.preparedQuery("SELECT c.*, u.username FROM comments c JOIN users u ON c.user_id = u.id WHERE c.id = ?")
      .execute(Tuple.of(Integer.parseInt(id)), ar -> {
        if (ar.succeeded() && ar.result().size() > 0) {
          Row row = ar.result().iterator().next();
          JsonObject comment = new JsonObject()
            .put("id", row.getInteger("id"))
            .put("userId", row.getInteger("user_id"))
            .put("recipeId", row.getInteger("recipe_id"))
            .put("content", row.getString("content"))
            .put("createdAt", row.getLocalDateTime("created_at").toString())
            .put("updatedAt", row.getLocalDateTime("updated_at").toString())
            .put("username", row.getString("username"));

          context.response()
            .putHeader("content-type", "application/json")
            .end(comment.encode());
        } else {
          context.response()
            .setStatusCode(404)
            .end(new JsonObject().put("message", "Comment not found").encode());
        }
      });
  }
  private void updateComment(RoutingContext context) {
    String commentId = context.pathParam("id");
    Integer userId = context.user().principal().getInteger("userId");
    JsonObject body = context.getBodyAsJson();

    // Validate input
    if (body == null || !body.containsKey("content")) {
      context.response()
        .setStatusCode(400)
        .end(new JsonObject().put("message", "Comment content is required").encode());
      return;
    }

    String content = body.getString("content");
    if (content == null || content.trim().isEmpty()) {
      context.response()
        .setStatusCode(400)
        .end(new JsonObject().put("message", "Comment content cannot be empty").encode());
      return;
    }

    // Check if the comment exists and belongs to the user
    client.preparedQuery("SELECT user_id FROM comments WHERE id = ?")
      .execute(Tuple.of(Integer.parseInt(commentId)), ar -> {
        if (ar.succeeded() && ar.result().size() > 0) {
          Integer commentUserId = ar.result().iterator().next().getInteger("user_id");

          if (!userId.equals(commentUserId)) {
            context.response()
              .setStatusCode(403)
              .end(new JsonObject().put("message", "You can only update your own comments").encode());
            return;
          }

          // Update the comment
          String sql = "UPDATE comments SET content = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
          client.preparedQuery(sql).execute(Tuple.of(content, Integer.parseInt(commentId)), updateResult -> {
            if (updateResult.succeeded()) {
              // Fetch the updated comment
              client.preparedQuery("SELECT c.*, u.username FROM comments c JOIN users u ON c.user_id = u.id WHERE c.id = ?")
                .execute(Tuple.of(Integer.parseInt(commentId)), fetchResult -> {
                  if (fetchResult.succeeded() && fetchResult.result().size() > 0) {
                    Row row = fetchResult.result().iterator().next();
                    JsonObject comment = new JsonObject()
                      .put("id", row.getInteger("id"))
                      .put("userId", row.getInteger("user_id"))
                      .put("recipeId", row.getInteger("recipe_id"))
                      .put("content", row.getString("content"))
                      .put("createdAt", row.getLocalDateTime("created_at").toString())
                      .put("updatedAt", row.getLocalDateTime("updated_at").toString())
                      .put("username", row.getString("username"));

                    context.response()
                      .setStatusCode(200)
                      .putHeader("content-type", "application/json")
                      .end(comment.encode());
                  } else {
                    context.response()
                      .setStatusCode(500)
                      .end(new JsonObject().put("message", "Failed to fetch updated comment").encode());
                  }
                });
            } else {
              context.response()
                .setStatusCode(500)
                .end(new JsonObject().put("message", "Failed to update comment: " + updateResult.cause().getMessage()).encode());
            }
          });
        } else {
          context.response()
            .setStatusCode(404)
            .end(new JsonObject().put("message", "Comment not found").encode());
        }
      });
  }
  private void deleteComment(RoutingContext context) {
    String commentId = context.pathParam("id");
    Integer userId = context.user().principal().getInteger("userId");

    // Check if the comment exists and belongs to the user
    client.preparedQuery("SELECT user_id FROM comments WHERE id = ?")
      .execute(Tuple.of(Integer.parseInt(commentId)), ar -> {
        if (ar.succeeded() && ar.result().size() > 0) {
          Integer commentUserId = ar.result().iterator().next().getInteger("user_id");

          if (!userId.equals(commentUserId)) {
            context.response()
              .setStatusCode(403)
              .end(new JsonObject().put("message", "You can only delete your own comments").encode());
            return;
          }

          // Delete the comment
          String sql = "DELETE FROM comments WHERE id = ?";
          client.preparedQuery(sql).execute(Tuple.of(Integer.parseInt(commentId)), deleteResult -> {
            if (deleteResult.succeeded()) {
              context.response()
                .setStatusCode(200)
                .end(new JsonObject().put("message", "Comment deleted successfully").encode());
            } else {
              context.response()
                .setStatusCode(500)
                .end(new JsonObject().put("message", "Failed to delete comment: " + deleteResult.cause().getMessage()).encode());
            }
          });
        } else {
          context.response()
            .setStatusCode(404)
            .end(new JsonObject().put("message", "Comment not found").encode());
        }
      });
  }
  private void getUserComments(RoutingContext context) {
    String userId = context.pathParam("userId");

    // Validate the user ID
    try {
      int id = Integer.parseInt(userId);
    } catch (NumberFormatException e) {
      context.response()
        .setStatusCode(400)
        .end(new JsonObject().put("message", "Invalid user ID: must be a number").encode());
      return;
    }

    // Check if the user exists
    client.preparedQuery("SELECT id FROM users WHERE id = ?")
      .execute(Tuple.of(Integer.parseInt(userId)), userCheck -> {
        if (userCheck.succeeded() && userCheck.result().size() > 0) {
          // Fetch all comments by the user
          String sql = "SELECT c.*, r.title AS recipe_title FROM comments c JOIN recipes r ON c.recipe_id = r.id WHERE c.user_id = ? ORDER BY c.created_at DESC";
          client.preparedQuery(sql).execute(Tuple.of(Integer.parseInt(userId)), ar -> {
            if (ar.succeeded()) {
              List<JsonObject> comments = new ArrayList<>();
              ar.result().forEach(row -> {
                JsonObject comment = new JsonObject()
                  .put("id", row.getInteger("id"))
                  .put("userId", row.getInteger("user_id"))
                  .put("recipeId", row.getInteger("recipe_id"))
                  .put("recipeTitle", row.getString("recipe_title"))
                  .put("content", row.getString("content"))
                  .put("createdAt", row.getLocalDateTime("created_at").toString())
                  .put("updatedAt", row.getLocalDateTime("updated_at").toString());

                comments.add(comment);
              });

              context.response()
                .setStatusCode(200)
                .putHeader("content-type", "application/json")
                .end(new JsonArray(comments).encode());
            } else {
              context.response()
                .setStatusCode(500)
                .end(new JsonObject().put("message", "Failed to fetch user comments: " + ar.cause().getMessage()).encode());
            }
          });
        } else {
          context.response()
            .setStatusCode(404)
            .end(new JsonObject().put("message", "User not found").encode());
        }
      });
  }


}
