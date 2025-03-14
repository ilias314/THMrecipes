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

    // ============================================================
    // Auth Routes
    // ============================================================

    /**
     * @api {post} /register Register a new user
     * @apiName RegisterUser
     * @apiGroup Auth
     *
     * @apiParam {String} username User's unique username.
     * @apiParam {String} email User's email address.
     * @apiParam {String} password User's password. Must contain at least one uppercase letter and two digits.
     *
     * @apiSuccess (201) {String} message Confirmation message.
     * @apiError (400) {String} message Missing required fields or invalid password.
     * @apiError (500) {String} message Error during registration.
     */
    router.post("/register").handler(this::register);

    /**
     * @api {post} /login User login
     * @apiName LoginUser
     * @apiGroup Auth
     *
     * @apiParam {String} email User's email address.
     * @apiParam {String} password User's password.
     *
     * @apiSuccess {String} accessToken JWT access token.
     * @apiSuccess {String} refreshToken JWT refresh token.
     * @apiSuccess {Number} userId The user's ID.
     * @apiError (401) {String} message Incorrect credentials or user not found.
     */
    router.post("/login").handler(this::login);

    /**
     * @api {post} /logout User logout
     * @apiName LogoutUser
     * @apiGroup Auth
     *
     * @apiSuccess {String} message Logout confirmation.
     */
    router.post("/logout").handler(this::logout);

    // ============================================================
    // User Routes
    // ============================================================

    /**
     * @api {post} /users Create a new user (Admin or self-registration)
     * @apiName CreateUser
     * @apiGroup User
     *
     * @apiParam {String} username User's username.
     * @apiParam {String} email User's email.
     * @apiParam {String} password User's password.
     *
     * @apiSuccess (201) {String} message User created successfully.
     * @apiError (400) {String} message Missing required fields.
     * @apiError (500) {String} message Error creating user.
     */
    router.post("/users").handler(this::createUser);

    /**
     * @api {get} /users Get all users
     * @apiName GetUsers
     * @apiGroup User
     *
     * @apiSuccess {Object[]} users List of users.
     * @apiSuccess {Number} users.id User ID.
     * @apiSuccess {String} users.username Username.
     * @apiSuccess {String} users.email Email address.
     * @apiError (500) {String} message Error retrieving users.
     */
    router.get("/users").handler(this::getAllUsers);

    /**
     * @api {get} /users/:id Get a user by ID
     * @apiName GetUserById
     * @apiGroup User
     *
     * @apiParam {Number} id User's unique ID.
     *
     * @apiSuccess {Number} id User ID.
     * @apiSuccess {String} username Username.
     * @apiSuccess {String} email Email address.
     * @apiError (404) {String} message User not found.
     */
    router.get("/users/:id").handler(this::getUserById);

    /**
     * @api {put} /users/:id Update user profile
     * @apiName UpdateUser
     * @apiGroup User
     *
     * @apiHeader {String} Authorization Bearer token.
     * @apiParam {Number} id User's unique ID.
     * @apiParam {String} username New username.
     * @apiParam {String} email New email address.
     * @apiParam {String} password New password (must contain at least one uppercase letter and two digits).
     *
     * @apiSuccess {String} message User updated successfully.
     * @apiError (400) {String} message Missing required data or invalid input.
     * @apiError (401) {String} message Unauthorized.
     * @apiError (403) {String} message Forbidden – users can only update their own profile.
     */
    router.put("/users/:id")
      .handler(JWTAuthHandler.create(jwtProvider))
      .handler(this::updateUser);

    /**
     * @api {delete} /users/:id Delete a user
     * @apiName DeleteUser
     * @apiGroup User
     *
     * @apiHeader {String} Authorization Bearer token.
     * @apiParam {Number} id User's unique ID.
     *
     * @apiSuccess (204) No Content.
     * @apiError (401) {String} message Unauthorized.
     * @apiError (403) {String} message Forbidden – users can only delete their own account.
     * @apiError (500) {String} message Error deleting user.
     */
    router.delete("/users/:id")
      .handler(JWTAuthHandler.create(jwtProvider))
      .handler(this::deleteUser);

    // ============================================================
    // Recipe Routes
    // ============================================================

    JWTAuthHandler jwtAuthHandler = JWTAuthHandler.create(jwtProvider);
    router.route("/recipes*").handler(jwtAuthHandler);

    /**
     * @api {post} /recipes Create a new recipe
     * @apiName AddRecipe
     * @apiGroup Recipe
     *
     * @apiHeader {String} Authorization Bearer token.
     * @apiParam {String} title Recipe title.
     * @apiParam {String} description Recipe description.
     * @apiParam {String} ingredients Ingredients as a comma-separated string.
     * @apiParam {String} instructions Instructions as a comma-separated string.
     * @apiParam {String} [image_url] Optional image URL.
     *
     * @apiSuccess (201) {String} message Recipe created successfully.
     * @apiError (400) {String} message Missing required fields.
     * @apiError (500) {String} message Error saving recipe.
     */
    router.post("/recipes").handler(this::addRecipe);

    /**
     * @api {get} /recipes Get all recipes
     * @apiName GetRecipes
     * @apiGroup Recipe
     *
     * @apiHeader {String} Authorization Bearer token.
     *
     * @apiSuccess {Object[]} recipes List of recipes.
     * @apiSuccess {Number} recipes.id Recipe ID.
     * @apiSuccess {Number} recipes.user_id Creator's user ID.
     * @apiSuccess {String} recipes.title Recipe title.
     * @apiSuccess {String} recipes.description Recipe description.
     * @apiSuccess {String} recipes.image_url Recipe image URL.
     * @apiSuccess {String[]} recipes.ingredients List of ingredients.
     * @apiSuccess {String[]} recipes.instructions List of instructions.
     * @apiError (500) {String} message Error retrieving recipes.
     */
    router.get("/recipes").handler(this::getAllRecipes);

    /**
     * @api {get} /recipes/:id Get recipe details
     * @apiName GetRecipeById
     * @apiGroup Recipe
     *
     * @apiHeader {String} Authorization Bearer token.
     * @apiParam {Number} id Recipe ID.
     *
     * @apiSuccess {Number} id Recipe ID.
     * @apiSuccess {Number} user_id Creator's user ID.
     * @apiSuccess {String} title Recipe title.
     * @apiSuccess {String} description Recipe description.
     * @apiSuccess {String} image_url Recipe image URL.
     * @apiSuccess {String[]} ingredients Array of ingredients.
     * @apiSuccess {String[]} instructions Array of instructions.
     * @apiError (404) {String} message Recipe not found.
     */
    router.get("/recipes/:id").handler(this::getRecipesById);

    /**
     * @api {put} /recipes/:id Update a recipe
     * @apiName UpdateRecipe
     * @apiGroup Recipe
     *
     * @apiHeader {String} Authorization Bearer token.
     * @apiParam {Number} id Recipe ID.
     * @apiParam {String} title Updated title.
     * @apiParam {String} description Updated description.
     * @apiParam {String} ingredients Updated ingredients (comma-separated).
     * @apiParam {String} instructions Updated instructions (comma-separated).
     * @apiParam {String} [image_url] Optional updated image URL.
     *
     * @apiSuccess {String} message Recipe updated successfully.
     * @apiError (400) {String} message Missing required fields.
     * @apiError (403) {String} message Forbidden – users can only update their own recipes.
     * @apiError (404) {String} message Recipe not found.
     * @apiError (500) {String} message Error updating recipe.
     */
    router.put("/recipes/:id")
      .handler(JWTAuthHandler.create(jwtProvider))
      .handler(this::updateRecipe);

    /**
     * @api {delete} /recipes/:id Delete a recipe
     * @apiName DeleteRecipe
     * @apiGroup Recipe
     *
     * @apiHeader {String} Authorization Bearer token.
     * @apiParam {Number} id Recipe ID.
     *
     * @apiSuccess {String} message Recipe deleted successfully.
     * @apiError (403) {String} message Forbidden – users can only delete their own recipes.
     * @apiError (404) {String} message Recipe not found.
     * @apiError (500) {String} message Error deleting recipe.
     */
    router.delete("/recipes/:id")
      .handler(JWTAuthHandler.create(jwtProvider))
      .handler(this::deleteRecipe);

    /**
     * @api {get} /users/:userId/recipes Get recipes by a specific user
     * @apiName GetRecipesByUser
     * @apiGroup Recipe
     *
     * @apiHeader {String} Authorization Bearer token.
     * @apiParam {Number} userId User's unique ID.
     *
     * @apiSuccess {Object[]} recipes List of recipes created by the user.
     * @apiError (400) {String} message User ID is required.
     * @apiError (500) {String} message Error retrieving user's recipes.
     */
    router.get("/users/:userId/recipes").handler(this::getRecipesByUserId);

    /**
     * @api {get} /recipe/search Search for recipes
     * @apiName SearchRecipes
     * @apiGroup Recipe
     *
     * @apiHeader {String} Authorization Bearer token.
     * @apiParam {String} q Search query string.
     *
     * @apiSuccess {Object[]} recipes List of recipes matching the query.
     * @apiError (400) {String} message Missing or empty search query.
     * @apiError (500) {String} message Error searching for recipes.
     */
    router.get("/recipe/search").handler(this::searchRecipes);

    // ============================================================
    // Rating Routes
    // ============================================================
    router.route("/comments*").handler(jwtAuthHandler);
    router.route("/ratings*").handler(jwtAuthHandler);

    /**
     * @api {post} /recipes/:recipeId/ratings Add a rating to a recipe
     * @apiName AddRating
     * @apiGroup Rating
     *
     * @apiHeader {String} Authorization Bearer token.
     * @apiParam {Number} recipeId Recipe ID.
     * @apiParam {Number} rating Rating value (1-5).
     *
     * @apiSuccess (201) {String} message Rating added successfully.
     * @apiError (400) {String} message Rating value is required or invalid.
     * @apiError (500) {String} message Error adding rating.
     */
    router.post("/recipes/:recipeId/ratings").handler(this::addRating);

    /**
     * @api {get} /recipes/:recipeId/ratings Get all ratings for a recipe
     * @apiName GetRecipeRatings
     * @apiGroup Rating
     *
     * @apiHeader {String} Authorization Bearer token.
     * @apiParam {Number} recipeId Recipe ID.
     *
     * @apiSuccess {Object[]} ratings List of ratings.
     * @apiSuccess {Number} ratings.id Rating ID.
     * @apiSuccess {Number} ratings.userId User ID.
     * @apiSuccess {Number} ratings.recipeId Recipe ID.
     * @apiSuccess {Number} ratings.rating Rating value.
     * @apiSuccess {String} ratings.createdAt Creation timestamp.
     * @apiSuccess {String} ratings.username Username of the rater.
     * @apiError (500) {String} message Error retrieving ratings.
     */
    router.get("/recipes/:recipeId/ratings").handler(this::getRecipeRatings);

    /**
     * @api {get} /ratings/:id Get a rating by ID
     * @apiName GetRatingById
     * @apiGroup Rating
     *
     * @apiHeader {String} Authorization Bearer token.
     * @apiParam {Number} id Rating ID.
     *
     * @apiSuccess {Number} id Rating ID.
     * @apiSuccess {Number} userId User ID.
     * @apiSuccess {Number} recipeId Recipe ID.
     * @apiSuccess {Number} rating Rating value.
     * @apiSuccess {String} createdAt Creation timestamp.
     * @apiSuccess {String} username Username of the rater.
     * @apiError (404) {String} message Rating not found.
     */
    router.get("/ratings/:id").handler(this::getRatingById);

    /**
     * @api {put} /ratings/:id Update a rating
     * @apiName UpdateRating
     * @apiGroup Rating
     *
     * @apiHeader {String} Authorization Bearer token.
     * @apiParam {Number} id Rating ID.
     * @apiParam {Number} rating New rating value (1-5).
     *
     * @apiSuccess {Object} rating Updated rating object.
     * @apiError (400) {String} message Missing or invalid rating value.
     * @apiError (403) {String} message Forbidden – users can only update their own ratings.
     * @apiError (404) {String} message Rating not found.
     * @apiError (500) {String} message Error updating rating.
     */
    router.put("/ratings/:id").handler(this::updateRating);

    /**
     * @api {delete} /ratings/:id Delete a rating
     * @apiName DeleteRating
     * @apiGroup Rating
     *
     * @apiHeader {String} Authorization Bearer token.
     * @apiParam {Number} id Rating ID.
     *
     * @apiSuccess {String} message Rating deleted successfully.
     * @apiError (403) {String} message Forbidden – users can only delete their own ratings.
     * @apiError (404) {String} message Rating not found.
     * @apiError (500) {String} message Error deleting rating.
     */
    router.delete("/ratings/:id").handler(this::deleteRating);

    /**
     * @api {get} /users/:userId/ratings Get all ratings by a user
     * @apiName GetUserRatings
     * @apiGroup Rating
     *
     * @apiHeader {String} Authorization Bearer token.
     * @apiParam {Number} userId User ID.
     *
     * @apiSuccess {Object[]} ratings List of ratings by the user.
     * @apiError (404) {String} message User not found.
     * @apiError (500) {String} message Error retrieving user ratings.
     */
    router.get("/users/:userId/ratings").handler(this::getUserRatings);

    // ============================================================
    // Comment Routes
    // ============================================================
    /**
     * @api {post} /recipes/:recipeId/comments Add a comment to a recipe
     * @apiName AddComment
     * @apiGroup Comment
     *
     * @apiHeader {String} Authorization Bearer token.
     * @apiParam {Number} recipeId Recipe ID.
     * @apiParam {String} content Comment content.
     *
     * @apiSuccess (201) {Object} comment Created comment object.
     * @apiError (400) {String} message Missing or empty comment content.
     * @apiError (404) {String} message Recipe not found.
     * @apiError (500) {String} message Error adding comment.
     */
    router.post("/recipes/:recipeId/comments").handler(this::addComment);

    /**
     * @api {get} /recipes/:recipeId/comments Get all comments for a recipe
     * @apiName GetRecipeComments
     * @apiGroup Comment
     *
     * @apiHeader {String} Authorization Bearer token.
     * @apiParam {Number} recipeId Recipe ID.
     *
     * @apiSuccess {Object[]} comments List of comments.
     * @apiSuccess {Number} comments.id Comment ID.
     * @apiSuccess {Number} comments.userId User ID.
     * @apiSuccess {Number} comments.recipeId Recipe ID.
     * @apiSuccess {String} comments.content Comment content.
     * @apiSuccess {String} comments.createdAt Creation timestamp.
     * @apiSuccess {String} comments.updatedAt Update timestamp.
     * @apiSuccess {String} comments.username Username of the commenter.
     * @apiError (404) {String} message Recipe not found.
     * @apiError (500) {String} message Error retrieving comments.
     */
    router.get("/recipes/:recipeId/comments").handler(this::getRecipeComments);

    /**
     * @api {get} /comments/:id Get a comment by ID
     * @apiName GetCommentById
     * @apiGroup Comment
     *
     * @apiHeader {String} Authorization Bearer token.
     * @apiParam {Number} id Comment ID.
     *
     * @apiSuccess {Number} id Comment ID.
     * @apiSuccess {Number} userId User ID.
     * @apiSuccess {Number} recipeId Recipe ID.
     * @apiSuccess {String} content Comment content.
     * @apiSuccess {String} createdAt Creation timestamp.
     * @apiSuccess {String} updatedAt Update timestamp.
     * @apiSuccess {String} username Username of the commenter.
     * @apiError (404) {String} message Comment not found.
     */
    router.get("/comments/:id").handler(this::getCommentById);

    /**
     * @api {put} /comments/:id Update a comment
     * @apiName UpdateComment
     * @apiGroup Comment
     *
     * @apiHeader {String} Authorization Bearer token.
     * @apiParam {Number} id Comment ID.
     * @apiParam {String} content Updated comment content.
     *
     * @apiSuccess {Object} comment Updated comment object.
     * @apiError (400) {String} message Missing or empty comment content.
     * @apiError (403) {String} message Forbidden – users can only update their own comments.
     * @apiError (404) {String} message Comment not found.
     * @apiError (500) {String} message Error updating comment.
     */
    router.put("/comments/:id").handler(this::updateComment);

    /**
     * @api {delete} /comments/:id Delete a comment
     * @apiName DeleteComment
     * @apiGroup Comment
     *
     * @apiHeader {String} Authorization Bearer token.
     * @apiParam {Number} id Comment ID.
     *
     * @apiSuccess {String} message Comment deleted successfully.
     * @apiError (403) {String} message Forbidden – users can only delete their own comments.
     * @apiError (404) {String} message Comment not found.
     * @apiError (500) {String} message Error deleting comment.
     */
    router.delete("/comments/:id").handler(this::deleteComment);

    /**
     * @api {get} /users/:userId/comments Get all comments by a user
     * @apiName GetUserComments
     * @apiGroup Comment
     *
     * @apiHeader {String} Authorization Bearer token.
     * @apiParam {Number} userId User's unique ID.
     *
     * @apiSuccess {Object[]} comments List of comments made by the user.
     * @apiError (404) {String} message User not found.
     * @apiError (500) {String} message Error retrieving comments.
     */
    router.get("/users/:userId/comments").handler(this::getUserComments);

    // ============================================================
    // Token & File Routes
    // ============================================================
    /**
     * @api {post} /refresh-token Refresh JWT access token
     * @apiName RefreshToken
     * @apiGroup Auth
     *
     * @apiParam {String} refreshToken Valid refresh token.
     *
     * @apiSuccess {String} accessToken New JWT access token.
     * @apiError (400) {String} message Refresh token is required.
     * @apiError (401) {String} message Invalid refresh token.
     */
    router.post("/refresh-token").handler(this::refreshToken);

    /**
     * @api {post} /upload Upload an image file
     * @apiName UploadImage
     * @apiGroup File
     *
     * @apiHeader {Content-Type} multipart/form-data
     * @apiParam {File} file Image file to be uploaded.
     *
     * @apiSuccess {String} image_url URL of the saved image.
     * @apiError (500) {String} message Error saving image.
     */
    router.post("/upload")
      .handler(BodyHandler.create().setUploadsDirectory("images"))
      .handler(this::uploadImage);

    // ============================================================
    // Wishlist Routes
    // ============================================================
    /**
     * @api {post} /wishlist Add a recipe to the wishlist
     * @apiName AddToWishlist
     * @apiGroup Wishlist
     *
     * @apiParam {Number} user_id User's ID.
     * @apiParam {Number} recipe_id Recipe's ID.
     *
     * @apiSuccess (201) {String} message Added to wishlist.
     * @apiError (409) {String} message Item is already in your wishlist.
     * @apiError (400) {String} message Missing user_id or recipe_id.
     * @apiError (500) {String} message Error adding to wishlist.
     */
    router.post("/wishlist").handler(this::addToWishlist);

    /**
     * @api {get} /wishlist/:user_id Get a user's wishlist
     * @apiName GetWishlist
     * @apiGroup Wishlist
     *
     * @apiParam {Number} user_id User's ID.
     *
     * @apiSuccess {Object[]} wishlist List of recipes in the wishlist.
     * @apiError (400) {String} message Invalid user ID.
     * @apiError (500) {String} message Error retrieving wishlist.
     */
    router.get("/wishlist/:user_id").handler(this::getWishlist);

    /**
     * @api {delete} /wishlist/:user_id/:recipe_id Remove a recipe from the wishlist
     * @apiName RemoveFromWishlist
     * @apiGroup Wishlist
     *
     * @apiParam {Number} user_id User's ID.
     * @apiParam {Number} recipe_id Recipe's ID.
     *
     * @apiSuccess {String} message Removed from wishlist.
     * @apiError (400) {String} message Invalid user ID or recipe ID.
     * @apiError (500) {String} message Error removing from wishlist.
     */
    router.delete("/wishlist/:user_id/:recipe_id").handler(this::removeFromWishlist);

    // ============================================================
    // Start HTTP Server
    // ============================================================
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

  // ============================================================
  // Handler Implementations
  // ============================================================

  // ----- User Handlers -----

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

  private void deleteUser(RoutingContext context) {
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
    String deleteRecipesQuery = "DELETE FROM recipes WHERE user_id = ?";
    String deleteUserQuery = "DELETE FROM users WHERE id = ?";

    client.getConnection(conn -> {
      if (conn.succeeded()) {
        SqlConnection connection = conn.result();
        connection.begin(tx -> {
          if (tx.succeeded()) {
            connection.preparedQuery(deleteRecipesQuery).execute(Tuple.of(Integer.parseInt(id)), ar -> {
              if (ar.succeeded()) {
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

  // ----- Auth Handlers -----

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

  private void login(RoutingContext context) {
    JsonObject body = context.getBodyAsJson();
    String sql = "SELECT id, password FROM users WHERE email = ?";
    client.preparedQuery(sql).execute(Tuple.of(body.getString("email")), ar -> {
      if (ar.succeeded() && ar.result().size() > 0) {
        Row row = ar.result().iterator().next();
        String storedPassword = row.getString("password");
        if (BCrypt.checkpw(body.getString("password"), storedPassword)) {
          int userId = row.getInteger("id");
          String accessToken = jwtProvider.generateToken(
            new JsonObject().put("userId", userId),
            new JWTOptions().setExpiresInMinutes(60)
          );
          String refreshToken = jwtProvider.generateToken(
            new JsonObject().put("userId", userId),
            new JWTOptions().setExpiresInMinutes(1440)
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

  private void logout(RoutingContext context) {
    context.response().setStatusCode(200).end("Erfolgreich abgemeldet.");
  }

  // ----- Recipe Handlers -----

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
    String title = body.getString("title");
    String description = body.getString("description");
    String ingredients = body.getString("ingredients");
    String instructions = body.getString("instructions");
    String imageUrl = body.containsKey("image_url") ? body.getString("image_url") : "/images/default.png";
    System.out.println("📦 Received recipe data:");
    System.out.println("Title: " + title);
    System.out.println("Description: " + description);
    System.out.println("Ingredients (Plain Text): " + ingredients);
    System.out.println("Instructions (Plain Text): " + instructions);
    System.out.println("Image URL: " + imageUrl);
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
          connection.close();
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
              String ingredientsText = row.getString("ingredients");
              recipe.put("ingredients", new JsonArray(Arrays.asList(ingredientsText.split(",\\s*"))));
              String instructionsText = row.getString("instructions");
              recipe.put("instructions", new JsonArray(Arrays.asList(instructionsText.split(",\\s*"))));
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
            connection.close();
            if (ar.succeeded() && ar.result().size() > 0) {
              Row row = ar.result().iterator().next();
              JsonObject recipe = new JsonObject()
                .put("id", row.getInteger("id"))
                .put("user_id", row.getInteger("user_id"))
                .put("title", row.getString("title"))
                .put("description", row.getString("description"))
                .put("image_url", row.getString("image_url"))
                .put("created_at", row.getLocalDateTime("created_at").toString());
              String ingredientsText = row.getString("ingredients");
              String instructionsText = row.getString("instructions");
              recipe.put("ingredients", new JsonArray(Arrays.asList(ingredientsText.split(",\\s*"))));
              recipe.put("instructions", new JsonArray(Arrays.asList(instructionsText.split(",\\s*"))));
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

  private void updateRecipe(RoutingContext context) {
    String recipeId = context.pathParam("id");
    JsonObject body = context.getBodyAsJson();
    Integer userIdFromToken = context.user().principal().getInteger("userId");
    if (!body.containsKey("title") || !body.containsKey("description") ||
      !body.containsKey("ingredients") || !body.containsKey("instructions")) {
      context.response()
        .setStatusCode(400)
        .end(new JsonObject().put("message", "Missing required fields: title, description, ingredients, instructions.").encode());
      return;
    }
    String checkOwnershipQuery = "SELECT user_id FROM recipes WHERE id = ?";
    client.preparedQuery(checkOwnershipQuery).execute(Tuple.of(Integer.parseInt(recipeId)), checkResult -> {
      if (checkResult.succeeded() && checkResult.result().size() > 0) {
        int ownerId = checkResult.result().iterator().next().getInteger("user_id");
        if (ownerId != userIdFromToken) {
          context.response()
            .setStatusCode(403)
            .end(new JsonObject().put("message", "Forbidden: You can only modify your own recipes.").encode());
          return;
        }
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
    String sql = "SELECT * FROM recipes WHERE title LIKE ? OR ingredients LIKE ?";
    client.preparedQuery(sql).execute(Tuple.of("%" + query + "%", "%" + query + "%"), ar -> {
      if (ar.succeeded()) {
        RowSet<Row> rows = ar.result();
        List<Rezept> recipes = new ArrayList<>();
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
        context.response()
          .setStatusCode(200)
          .putHeader("content-type", "application/json")
          .end(Json.encodePrettily(recipes));
      } else {
        System.err.println("❌ Error searching recipes: " + ar.cause().getMessage());
        context.response()
          .setStatusCode(500)
          .end(new JsonObject().put("message", "Failed to search recipes: " + ar.cause().getMessage()).encode());
      }
    });
  }

  private void addRating(RoutingContext context) {
    JsonObject body = context.getBodyAsJson();
    Integer ratingValue;
    try {
      ratingValue = body.getInteger("rating");
      if (ratingValue == null) {
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
    if (ratingValue < 1 || ratingValue > 5) {
      context.response()
        .setStatusCode(400)
        .end(new JsonObject().put("message", "Rating must be between 1 and 5").encode());
      return;
    }
    Integer userId = context.user().principal().getInteger("userId");
    String recipeId = context.pathParam("recipeId");
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
    client.preparedQuery("SELECT id FROM recipes WHERE id = ?")
      .execute(Tuple.of(Integer.parseInt(recipeId)), recipeCheck -> {
        if (recipeCheck.succeeded() && recipeCheck.result().size() > 0) {
          client.preparedQuery("INSERT INTO comments (user_id, recipe_id, content) VALUES (?, ?, ?)")
            .execute(Tuple.of(userId, Integer.parseInt(recipeId), content), ar -> {
              if (ar.succeeded()) {
                client.preparedQuery("SELECT LAST_INSERT_ID() as id")
                  .execute(idResult -> {
                    if (idResult.succeeded() && idResult.result().size() > 0) {
                      Integer newId = idResult.result().iterator().next().getInteger("id");
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
          String sql = "UPDATE comments SET content = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
          client.preparedQuery(sql).execute(Tuple.of(content, Integer.parseInt(commentId)), updateResult -> {
            if (updateResult.succeeded()) {
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
    try {
      int id = Integer.parseInt(userId);
    } catch (NumberFormatException e) {
      context.response()
        .setStatusCode(400)
        .end(new JsonObject().put("message", "Invalid user ID: must be a number").encode());
      return;
    }
    client.preparedQuery("SELECT id FROM users WHERE id = ?")
      .execute(Tuple.of(Integer.parseInt(userId)), userCheck -> {
        if (userCheck.succeeded() && userCheck.result().size() > 0) {
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
    jwtProvider.authenticate(new JsonObject().put("token", refreshToken), authResult -> {
      if (authResult.succeeded()) {
        JsonObject userPrincipal = authResult.result().principal();
        int userId = userPrincipal.getInteger("userId");
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

  // ----- Wishlist Handlers -----

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
    String checkQuery = "SELECT COUNT(*) AS count FROM wishlist WHERE user_id = ? AND recipe_id = ?";
    client.preparedQuery(checkQuery).execute(Tuple.of(userId, recipeId), checkResult -> {
      if (checkResult.succeeded()) {
        int count = checkResult.result().iterator().next().getInteger("count");
        if (count > 0) {
          ctx.response()
            .setStatusCode(409)
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("message", "Item is already in your wishlist").encode());
        } else {
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
