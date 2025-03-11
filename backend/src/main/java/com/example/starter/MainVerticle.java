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
import java.util.*;

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

    vertx.fileSystem().exists("images", existsRes -> {
      if (!existsRes.succeeded() || !existsRes.result()) {
        vertx.fileSystem().mkdir("images", mkdirRes -> {
          if (mkdirRes.failed()) {
            System.err.println("Could not create images directory: " + mkdirRes.cause().getMessage());
          }
        });
      }
    });


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

    router.route("/images/*").handler(StaticHandler.create("images"));



    router.route("/protected-route").handler(JWTAuthHandler.create(jwtProvider));

    // Auth-Routen
    router.post("/register").handler(this::register);
    router.post("/login").handler(this::login);
    router.post("/logout").handler(this::logout);

    // User routes
    router.post("/users").handler(this::createUser);
    router.get("/users").handler(this::getAllUsers);
    router.get("/users/:id").handler(this::getUserById);
    router.put("/users/:id")
      .handler(JWTAuthHandler.create(jwtProvider))
      .handler(this::updateUser);

    router.delete("/users/:id")
      .handler(JWTAuthHandler.create(jwtProvider))
      .handler(this::deleteUser);


    // Recipe routes
    JWTAuthHandler jwtAuthHandler = JWTAuthHandler.create(jwtProvider);
    router.route("/recipes*").handler(jwtAuthHandler);
    router.post("/recipes").handler(this::addRecipe);
    router.get("/recipes").handler(this::getAllRecipes);
    router.get("/recipes/:id").handler(this::getRecipesById);

    router.put("/recipes/:id")
      .handler(JWTAuthHandler.create(jwtProvider))
      .handler(this::updateRecipe);

    router.delete("/recipes/:id")
      .handler(JWTAuthHandler.create(jwtProvider))
      .handler(this::deleteRecipe);


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

    router.post("/refresh-token").handler(this::refreshToken);

    router.post("/upload").handler(BodyHandler.create().setUploadsDirectory("images")).handler(this::uploadImage);

    // Wishlist routes
    router.post("/wishlist").handler(this::addToWishlist);
    router.get("/wishlist/:user_id").handler(this::getWishlist);
    router.delete("/wishlist/:user_id/:recipe_id").handler(this::removeFromWishlist);








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

  private void updateUser(RoutingContext context) {
    String id = context.pathParam("id");
    if (context.user() == null) {
      context.response().setStatusCode(401).end("Unauthorized: User not authenticated.");
      return;
    }
    JsonObject principal = context.user().principal();
    if (principal == null || !principal.containsKey("userId")) {
      context.response().setStatusCode(401).end("Unauthorized: User principal is invalid.");
      return;
    }
    Integer userIdFromToken = principal.getInteger("userId");
    if (!userIdFromToken.equals(Integer.parseInt(id))) {
      context.response().setStatusCode(403).end("Forbidden: You can only update your own profile.");
      return;
    }
    JsonObject body = context.getBodyAsJson();
    if (body == null || !body.containsKey("username") || !body.containsKey("email") || !body.containsKey("password")) {
      context.response().setStatusCode(400).end("❌ Fehlende Daten");
      return;
    }
    if (!isValidEmail(body.getString("email"))) {
      context.response().setStatusCode(400).end("Invalid email format.");
      return;
    }
    if (!isValidPassword(body.getString("password"))) {
      context.response().setStatusCode(400).end("Password must contain at least one uppercase letter and two digits.");
      return;
    }
    String hashedPassword = BCrypt.hashpw(body.getString("password"), BCrypt.gensalt());
    String sql = "UPDATE users SET username = ?, email = ?, password = ? WHERE id = ?";
    client.preparedQuery(sql).execute(Tuple.of(body.getString("username"), body.getString("email"), hashedPassword, Integer.parseInt(id)), ar -> {
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
    // Defensive check: if user is not set, respond with an unauthorized error.
    if (context.user() == null) {
      context.response().setStatusCode(401).end("Unauthorized: User not authenticated.");
      return;
    }

    String id = context.pathParam("id");
    Integer userIdFromToken = context.user().principal().getInteger("userId");
    if (!userIdFromToken.equals(Integer.parseInt(id))) {
      context.response().setStatusCode(403).end("Forbidden: You can only delete your own account.");
      return;
    }

    // SQL queries to delete recipes and user
    String deleteRecipesQuery = "DELETE FROM recipes WHERE user_id = ?";
    String deleteUserQuery = "DELETE FROM users WHERE id = ?";

    client.getConnection(conn -> {
      if (conn.succeeded()) {
        SqlConnection connection = conn.result();
        connection.begin(tx -> {
          if (tx.succeeded()) {
            // First, delete all recipes associated with the user
            connection.preparedQuery(deleteRecipesQuery).execute(Tuple.of(Integer.parseInt(id)), ar -> {
              if (ar.succeeded()) {
                // Then, delete the user
                connection.preparedQuery(deleteUserQuery).execute(Tuple.of(Integer.parseInt(id)), ar2 -> {
                  if (ar2.succeeded()) {
                    tx.result().commit(commit -> {
                      if (commit.succeeded()) {
                        context.response().setStatusCode(204).end();
                      } else {
                        tx.result().rollback();
                        context.response().setStatusCode(500).end("Failed to commit transaction.");
                      }
                      connection.close();
                    });
                  } else {
                    tx.result().rollback();
                    context.response().setStatusCode(500).end("Failed to delete user.");
                    connection.close();
                  }
                });
              } else {
                tx.result().rollback();
                context.response().setStatusCode(500).end("Failed to delete associated recipes.");
                connection.close();
              }
            });
          } else {
            context.response().setStatusCode(500).end("Failed to begin transaction.");
            connection.close();
          }
        });
      } else {
        context.response().setStatusCode(500).end("Failed to get database connection.");
      }
    });
  }


  // ✅ Benutzer registrieren
  private void register(RoutingContext context) {
    JsonObject body = context.getBodyAsJson();
    if (!isValidPassword(body.getString("password"))) {
      context.response()
        .setStatusCode(400)
        .end(new JsonObject().put("message", "Passwort muss mindestens einen Großbuchstaben und zwei Ziffern enthalten.").encode());
      return;
    }

    String hashedPassword = BCrypt.hashpw(body.getString("password"), BCrypt.gensalt());
    String sql = "INSERT INTO users (username, email, password) VALUES (?, ?, ?)";

    client.preparedQuery(sql).execute(Tuple.of(body.getString("username"), body.getString("email"), hashedPassword), ar -> {
      if (ar.succeeded()) {
        context.response()
          .setStatusCode(201)
          .end(new JsonObject().put("message", "Benutzer erfolgreich registriert.").encode());
      } else {
        context.response()
          .setStatusCode(500)
          .end(new JsonObject().put("message", "Fehler bei der Registrierung.").encode());
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
          int userId = row.getInteger("id");

          // Generate Access Token (short-lived)
          String accessToken = jwtProvider.generateToken(
            new JsonObject().put("userId", userId),
            new JWTOptions().setExpiresInMinutes(15)  // Shorter expiration time
          );

          // Generate Refresh Token (long-lived)
          String refreshToken = jwtProvider.generateToken(
            new JsonObject().put("userId", userId),
            new JWTOptions().setExpiresInMinutes(1440)  // 24 hours
          );

          context.response()
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject()
              .put("accessToken", accessToken)
              .put("refreshToken", refreshToken)
              .put("userId", userId)
              .encode());
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
  private void addRecipe(RoutingContext context) {
    JsonObject body = context.getBodyAsJson();
    int userId = context.user().principal().getInteger("userId");

    if (body == null || !body.containsKey("title") || !body.containsKey("description") ||
      !body.containsKey("ingredients") || !body.containsKey("instructions")) {
      context.response()
        .setStatusCode(400)
        .end(new JsonObject().put("message", "Missing required fields: title, description, ingredients, instructions.").encode());
      return;
    }

    // Extract values as Strings
    String title = body.getString("title");
    String description = body.getString("description");

    // ✅ Fix: Treat `ingredients` and `instructions` as Strings (not `JsonArray`)
    String ingredients = body.getString("ingredients");  // No `JsonArray`, just a plain text string
    String instructions = body.getString("instructions");  // No `JsonArray`, just a plain text string

    // Image URL (set default if not provided)
    String imageUrl = body.containsKey("image_url") ? body.getString("image_url") : "/images/default.png";

    // Debugging log
    System.out.println("📦 Received recipe data:");
    System.out.println("Title: " + title);
    System.out.println("Description: " + description);
    System.out.println("Ingredients (Plain Text): " + ingredients);
    System.out.println("Instructions (Plain Text): " + instructions);
    System.out.println("Image URL: " + imageUrl);

    // SQL Query to insert the recipe
    String sql = "INSERT INTO recipes (user_id, title, description, ingredients, instructions, image_url) VALUES (?, ?, ?, ?, ?, ?)";

    client.preparedQuery(sql).execute(Tuple.of(userId, title, description, ingredients, instructions, imageUrl), ar -> {
      if (ar.succeeded()) {
        System.out.println("✅ Recipe successfully saved: " + title);
        context.response()
          .setStatusCode(201)
          .end(new JsonObject().put("message", "✅ Recipe created successfully!").encode());
      } else {
        System.err.println("❌ Error saving recipe: " + ar.cause().getMessage());
        context.response()
          .setStatusCode(500)
          .end(new JsonObject().put("message", "❌ Failed to save recipe: " + ar.cause().getMessage()).encode());
      }
    });
  }




  private void getAllRecipes(RoutingContext context) {
    client.getConnection(conn -> {
      if (conn.succeeded()) {
        SqlConnection connection = conn.result();
        connection.query("SELECT * FROM recipes").execute(ar -> {
          connection.close(); // Always close the connection
          if (ar.succeeded()) {
            JsonArray recipes = new JsonArray();
            ar.result().forEach(row -> {
              JsonObject recipe = new JsonObject()
                .put("id", row.getInteger("id"))
                .put("user_id", row.getInteger("user_id"))
                .put("title", row.getString("title"))
                .put("description", row.getString("description"))
                .put("image_url", row.getString("image_url"))
                .put("created_at", row.getLocalDateTime("created_at").toString());

              // Parse ingredients and instructions from text to arrays
              String ingredientsText = row.getString("ingredients");
              recipe.put("ingredients", new JsonArray(Arrays.asList(ingredientsText.split(",\\s*")))); // Trim spaces
              String instructionsText = row.getString("instructions");
              recipe.put("instructions", new JsonArray(Arrays.asList(instructionsText.split(",\\s*")))); // Split by comma and optional spaces



              recipes.add(recipe);
            });

            context.response()
              .setStatusCode(200)
              .putHeader("content-type", "application/json")
              .end(recipes.encode());
          } else {
            System.err.println("❌ Error fetching recipes: " + ar.cause().getMessage());
            context.response()
              .setStatusCode(500)
              .end(new JsonObject().put("message", "Failed to fetch recipes: " + ar.cause().getMessage()).encode());
          }
        });
      } else {
        System.err.println("❌ Failed to connect to database: " + conn.cause().getMessage());
        context.response()
          .setStatusCode(500)
          .end(new JsonObject().put("message", "Failed to connect to database: " + conn.cause().getMessage()).encode());
      }
    });
  }

  private void getRecipesById(RoutingContext context) {
    String id = context.pathParam("id");

    // Validate recipe ID
    try {
      int recipeId = Integer.parseInt(id);
    } catch (NumberFormatException e) {
      context.response()
        .setStatusCode(400)
        .end(new JsonObject().put("message", "Invalid recipe ID: must be a number").encode());
      return;
    }

    client.getConnection(conn -> {
      if (conn.succeeded()) {
        SqlConnection connection = conn.result();
        connection.preparedQuery("SELECT * FROM recipes WHERE id = ?")
          .execute(Tuple.of(Integer.parseInt(id)), ar -> {
            connection.close(); // Always close the connection
            if (ar.succeeded() && ar.result().size() > 0) {
              Row row = ar.result().iterator().next();

              // Create a JsonObject for the recipe
              JsonObject recipe = new JsonObject()
                .put("id", row.getInteger("id"))
                .put("user_id", row.getInteger("user_id"))
                .put("title", row.getString("title"))
                .put("description", row.getString("description"))
                .put("image_url", row.getString("image_url"))
                .put("created_at", row.getLocalDateTime("created_at").toString());

              // Parse ingredients and instructions from text to arrays
              String ingredientsText = row.getString("ingredients");
              String instructionsText = row.getString("instructions");
              recipe.put("ingredients", new JsonArray(Arrays.asList(ingredientsText.split(",\\s*")))); // Trim spaces
              recipe.put("instructions", new JsonArray(Arrays.asList(instructionsText.split(",\\s*")))); // Split by ". "


              // Send the response
              context.response()
                .setStatusCode(200)
                .putHeader("content-type", "application/json")
                .end(recipe.encode());
            } else {
              context.response()
                .setStatusCode(404)
                .end(new JsonObject().put("message", "❌ Recipe not found.").encode());
            }
          });
      } else {
        System.err.println("❌ Failed to connect to database: " + conn.cause().getMessage());
        context.response()
          .setStatusCode(500)
          .end(new JsonObject().put("message", "Failed to connect to database: " + conn.cause().getMessage()).encode());
      }
    });
  }

  // Handler for updating a recipe
  private void updateRecipe(RoutingContext context) {
    String recipeId = context.pathParam("id");
    JsonObject body = context.getBodyAsJson();
    Integer userIdFromToken = context.user().principal().getInteger("userId");

    // Validate required fields
    if (!body.containsKey("title") || !body.containsKey("description") ||
      !body.containsKey("ingredients") || !body.containsKey("instructions")) {
      context.response()
        .setStatusCode(400)
        .end(new JsonObject().put("message", "Missing required fields: title, description, ingredients, instructions.").encode());
      return;
    }

    // Check if the recipe exists and belongs to the user
    String checkOwnershipQuery = "SELECT user_id FROM recipes WHERE id = ?";
    client.preparedQuery(checkOwnershipQuery).execute(Tuple.of(Integer.parseInt(recipeId)), checkResult -> {
      if (checkResult.succeeded() && checkResult.result().size() > 0) {
        int ownerId = checkResult.result().iterator().next().getInteger("user_id");

        // Ensure the user owns the recipe
        if (ownerId != userIdFromToken) {
          context.response()
            .setStatusCode(403)
            .end(new JsonObject().put("message", "Forbidden: You can only modify your own recipes.").encode());
          return;
        }

        // Proceed with update
        String updateQuery = "UPDATE recipes SET title = ?, description = ?, ingredients = ?, instructions = ?, image_url = ? WHERE id = ?";
        Tuple params = Tuple.of(
          body.getString("title", ""),
          body.getString("description", ""),
          body.getString("ingredients", ""),
          body.getString("instructions", ""),
          body.getString("image_url", "/images/default.png"),
          Integer.parseInt(recipeId)
        );

        client.preparedQuery(updateQuery).execute(params, updateResult -> {
          if (updateResult.succeeded()) {
            context.response()
              .setStatusCode(200)
              .end(new JsonObject().put("message", "✅ Recipe updated successfully!").encode());
          } else {
            System.err.println("❌ Error updating recipe: " + updateResult.cause().getMessage());
            context.response()
              .setStatusCode(500)
              .end(new JsonObject().put("message", "Failed to update recipe: " + updateResult.cause().getMessage()).encode());
          }
        });
      } else {
        context.response()
          .setStatusCode(404)
          .end(new JsonObject().put("message", "❌ Recipe not found.").encode());
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

        // Ensure the user owns the recipe
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
    if (userId == null || userId.isEmpty()) {
      routingContext.response()
        .setStatusCode(400)
        .end(new JsonObject().put("message", "User ID is required").encode());
      return;
    }

    client.getConnection(conn -> {
      if (conn.succeeded()) {
        SqlConnection connection = conn.result();
        connection.preparedQuery("SELECT * FROM recipes WHERE user_id = ?")
          .execute(Tuple.of(Integer.parseInt(userId)), ar -> {
            connection.close();
            if (ar.succeeded()) {
              RowSet<Row> rows = ar.result();
              List<Rezept> recipes = new ArrayList<>();

              for (Row row : rows) {
                Rezept rezept = new Rezept();
                rezept.setId(row.getInteger("id"));
                rezept.setUserId(row.getInteger("user_id"));
                rezept.setTitle(row.getString("title"));
                rezept.setDescription(row.getString("description"));
                rezept.setIngredients(Arrays.asList(row.getString("ingredients").split(",\\s*")));
                rezept.setInstructions(Arrays.asList(row.getString("instructions").split(",\\s*")));
                rezept.setImageUrl(row.getString("image_url"));
                rezept.setCreatedAt(row.getLocalDateTime("created_at").toString());

                // NEW: Set the image URL from the DB column "image_url"


                recipes.add(rezept);
              }

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
    JsonObject body = context.getBodyAsJson();

    // Safely parse the rating value
    Integer ratingValue;
    try {
      ratingValue = body.getInteger("rating"); // Try to get as integer
      if (ratingValue == null) {
        // If not an integer, try to parse as string
        String ratingStr = body.getString("rating");
        if (ratingStr != null) {
          ratingValue = Integer.parseInt(ratingStr);
        } else {
          context.response()
            .setStatusCode(400)
            .end(new JsonObject().put("message", "Rating value is required").encode());
          return;
        }
      }
    } catch (NumberFormatException e) {
      context.response()
        .setStatusCode(400)
        .end(new JsonObject().put("message", "Invalid rating format. Rating must be a number.").encode());
      return;
    }

    // Validate the rating value (1-5)
    if (ratingValue < 1 || ratingValue > 5) {
      context.response()
        .setStatusCode(400)
        .end(new JsonObject().put("message", "Rating must be between 1 and 5").encode());
      return;
    }

    // Get the authenticated user ID
    Integer userId = context.user().principal().getInteger("userId");
    String recipeId = context.pathParam("recipeId");

    // Insert the rating into the database
    String sql = "INSERT INTO ratings (user_id, recipe_id, rating) VALUES (?, ?, ?)";
    client.preparedQuery(sql).execute(Tuple.of(userId, Integer.parseInt(recipeId), ratingValue), ar -> {
      if (ar.succeeded()) {
        context.response()
          .setStatusCode(201)
          .end(new JsonObject().put("message", "Rating added successfully").encode());
      } else {
        context.response()
          .setStatusCode(500)
          .end(new JsonObject().put("message", "Failed to add rating: " + ar.cause().getMessage()).encode());
      }
    });
  }
  private void getRecipeRatings(RoutingContext context) {
    String recipeId = context.pathParam("recipeId");

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

          // Return an empty array if no ratings are found
          if (ratings.isEmpty()) {
            context.response()
              .putHeader("content-type", "application/json")
              .end(new JsonArray().encode());
          } else {
            context.response()
              .putHeader("content-type", "application/json")
              .end(new JsonArray(ratings).encode());
          }
        } else {
          context.response()
            .setStatusCode(500)
            .end(new JsonObject().put("message", "Failed to get ratings: " + ar.cause().getMessage()).encode());
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
  private void uploadImage(RoutingContext context) {
    context.fileUploads().forEach(file -> {
      String uploadedFileName = file.uploadedFileName();
      // Generate a unique file name by appending the current timestamp
      String targetFileName = "images/" + System.currentTimeMillis() + "-" + file.fileName();

      System.out.println("📤 Bild wird gespeichert unter: " + targetFileName);

      vertx.fileSystem().move(uploadedFileName, targetFileName, res -> {
        if (res.succeeded()) {
          String imageUrl = "/" + targetFileName;
          System.out.println("✅ Bild erfolgreich gespeichert: " + imageUrl);
          context.response().setStatusCode(200)
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("image_url", imageUrl).encode());
        } else {
          System.err.println("❌ Fehler beim Speichern des Bildes: " + res.cause().getMessage());
          context.response().setStatusCode(500).end("❌ Fehler beim Speichern des Bildes.");
        }
      });
    });
  }

  private void refreshToken(RoutingContext context) {
    JsonObject body = context.getBodyAsJson();

    if (body == null || !body.containsKey("refreshToken")) {
      context.response().setStatusCode(400).end(new JsonObject().put("message", "Refresh Token is required").encode());
      return;
    }

    String refreshToken = body.getString("refreshToken");

    // Verify the refresh token
    jwtProvider.authenticate(new JsonObject().put("token", refreshToken), authResult -> {
      if (authResult.succeeded()) {
        JsonObject userPrincipal = authResult.result().principal();
        int userId = userPrincipal.getInteger("userId");

        // Generate a new Access Token
        String newAccessToken = jwtProvider.generateToken(
          new JsonObject().put("userId", userId),
          new JWTOptions().setExpiresInMinutes(60)
        );

        context.response().setStatusCode(200).end(new JsonObject().put("accessToken", newAccessToken).encode());
      } else {
        context.response().setStatusCode(401).end(new JsonObject().put("message", "Invalid refresh token").encode());
      }
    });
  }
  // Rezept zur Wunschliste hinzufügen
  private void addToWishlist(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();

    if (!body.containsKey("user_id") || !body.containsKey("recipe_id")) {
      ctx.response()
        .setStatusCode(400)
        .putHeader("Content-Type", "application/json")
        .end(new JsonObject().put("error", "Missing user_id or recipe_id").encode());
      return;
    }

    int userId = body.getInteger("user_id");
    int recipeId = body.getInteger("recipe_id");

    // Zuerst prüfen, ob das Rezept bereits in der Wishlist ist
    String checkQuery = "SELECT COUNT(*) AS count FROM wishlist WHERE user_id = ? AND recipe_id = ?";

    client.preparedQuery(checkQuery).execute(Tuple.of(userId, recipeId), checkResult -> {
      if (checkResult.succeeded()) {
        int count = checkResult.result().iterator().next().getInteger("count");

        if (count > 0) {
          // Rezept ist bereits in der Wishlist
          ctx.response()
            .setStatusCode(409) // Conflict-Statuscode
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("message", "Item is already in your wishlist").encode());
        } else {
          // Rezept zur Wishlist hinzufügen
          String insertQuery = "INSERT INTO wishlist (user_id, recipe_id) VALUES (?, ?)";
          client.preparedQuery(insertQuery).execute(Tuple.of(userId, recipeId))
            .onSuccess(res -> ctx.response()
              .setStatusCode(201)
              .putHeader("Content-Type", "application/json")
              .end(new JsonObject().put("message", "Added to wishlist").encode()))
            .onFailure(err -> {
              err.printStackTrace();
              ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", err.getMessage()).encode());
            });
        }
      } else {
        ctx.response()
          .setStatusCode(500)
          .putHeader("Content-Type", "application/json")
          .end(new JsonObject().put("error", checkResult.cause().getMessage()).encode());
      }
    });
  }


  // Wunschliste für einen Benutzer abrufen
  private void getWishlist(RoutingContext ctx) {
    String userIdParam = ctx.pathParam("user_id");

    try {
      int userId = Integer.parseInt(userIdParam);

      String query = "SELECT r.id, r.title, r.description, r.image_url FROM wishlist w JOIN recipes r ON w.recipe_id = r.id WHERE w.user_id = ?";
      client.preparedQuery(query).execute(Tuple.of(userId), res -> {
        if (res.succeeded()) {
          JsonArray wishlist = new JsonArray();
          for (Row row : res.result()) {
            wishlist.add(new JsonObject()
              .put("id", row.getInteger("id"))
              .put("title", row.getString("title"))
              .put("description", row.getString("description"))
              .put("image_url", row.getString("image_url"))
            );
          }
          ctx.response().putHeader("content-type", "application/json").end(wishlist.encode());
        } else {
          ctx.response().setStatusCode(500).end(new JsonObject().put("error", res.cause().getMessage()).encode());
        }
      });
    } catch (NumberFormatException e) {
      ctx.response().setStatusCode(400).end(new JsonObject().put("message", "Invalid user ID").encode());
    }
  }

  // Rezept aus Wunschliste entfernen

  private void removeFromWishlist(RoutingContext ctx) {
    String userIdParam = ctx.pathParam("user_id");
    String recipeIdParam = ctx.pathParam("recipe_id");

    try {
      int userId = Integer.parseInt(userIdParam);
      int recipeId = Integer.parseInt(recipeIdParam);

      String query = "DELETE FROM wishlist WHERE user_id = ? AND recipe_id = ?";
      client.preparedQuery(query).execute(Tuple.of(userId, recipeId), res -> {
        if (res.succeeded()) {
          ctx.response().setStatusCode(200).end(new JsonObject().put("message", "Removed from wishlist").encode());
        } else {
          ctx.response().setStatusCode(500).end(new JsonObject().put("error", res.cause().getMessage()).encode());
        }
      });
    } catch (NumberFormatException e) {
      ctx.response().setStatusCode(400).end(new JsonObject().put("message", "Invalid user ID or recipe ID").encode());
    }
  }




}
