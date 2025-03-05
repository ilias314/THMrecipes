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

    vertx.createHttpServer().requestHandler(router).listen(8888, http -> {
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
    JsonObject body = context.getBodyAsJson();
    if (body == null || !body.containsKey("username") || !body.containsKey("email") || !body.containsKey("password")) {
      context.response().setStatusCode(400).end("❌ Fehlende Daten");
      return;
    }
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

  // 📌 DELETE: Benutzer löschen
  private void deleteUser(RoutingContext context) {
    String id = context.pathParam("id");
    client.preparedQuery("DELETE FROM users WHERE id = ?").execute(Tuple.of(Integer.parseInt(id)), ar -> {
      if (ar.succeeded() && ar.result().rowCount() > 0) {
        context.response().setStatusCode(204).end();
      } else {
        System.err.println("❌ Fehler beim Löschen des Benutzers: ID " + id);
        context.response().setStatusCode(404).end("Benutzer nicht gefunden");
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

          context.response().putHeader("content-type", "application/json").end(new JsonObject().put("token", token).encode());
        } else {
          context.response().setStatusCode(401).end("Ungültige Anmeldeinformationen.");
        }
      } else {
        context.response().setStatusCode(401).end("Benutzer nicht gefunden.");
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
    if (body == null) {
      routingContext.response()
        .setStatusCode(400)
        .end(new JsonObject().put("message", "Request body is required").encode());
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
}
