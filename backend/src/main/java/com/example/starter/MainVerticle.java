package com.example.starter;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;


import java.util.ArrayList;
import java.util.List;

public class MainVerticle extends AbstractVerticle {
  private MySQLPool client;

  @Override
  public void start(Promise<Void> startPromise) throws Exception {

    // Load configuration from the config.json file
    JsonObject config = vertx.fileSystem().readFileBlocking("config.json").toJsonObject();

    // Configure MySQL client
    MySQLConnectOptions connectOptions = new MySQLConnectOptions()
      .setHost(config.getJsonObject("db").getString("host", "localhost"))
      .setPort(config.getJsonObject("db").getInteger("port", 3306))
      .setDatabase(config.getJsonObject("db").getString("database", "recipes_db"))
      .setUser(config.getJsonObject("db").getString("user", "root"))
      .setPassword(config.getJsonObject("db").getString("password", "ErJUVVyAzJzEaosu"));

    PoolOptions poolOptions = new PoolOptions().setMaxSize(5);

    client = MySQLPool.pool(vertx, connectOptions, poolOptions);


    Router router =Router.router(vertx);
    router.route().handler(BodyHandler.create());

    router.post("/recipes").handler(this::addRecipe);
    router.get("/recipes").handler(this::getAllRecipes);
    router.get("/recipes/:id").handler(this::getRecipesById);
    router.put("/recipes/:id").handler(this::updateRecipe);
    router.delete("/recipes/:id").handler(this::deleteRecipe);


    vertx.createHttpServer()
      .requestHandler(router)
      .listen(8888).onComplete(http -> {
        if (http.succeeded()) {
          startPromise.complete();
          System.out.println("HTTP server started on port 8888");
      } else {
        startPromise.fail(http.cause());
      }
    });
  }

  private void addRecipe(RoutingContext routingContext) {
    JsonObject body = routingContext.getBodyAsJson();
    Rezept rezept = body.mapTo(Rezept.class);

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
              routingContext.fail(500);
            }
          });
      } else {
        routingContext.fail(conn.cause());
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
    String id = routingContext.request().getParam("id");
    JsonObject body = routingContext.getBodyAsJson();
    Rezept updatedRecipe = body.mapTo(Rezept.class);

    client.getConnection(conn -> {
      if (conn.succeeded()) {
        SqlConnection connection = conn.result();
        connection.preparedQuery("UPDATE recipes SET title = ?, description = ?, ingredients = ?, instructions = ? WHERE id = ?")
          .execute(Tuple.of(updatedRecipe.getTitle(), updatedRecipe.getDescription(), updatedRecipe.getIngredients(), updatedRecipe.getInstructions(), Integer.parseInt(id)), ar -> {
            connection.close();
            if (ar.succeeded()) {
              routingContext.response()
                .setStatusCode(200)
                .putHeader("content-type", "application/json")
                .end(Json.encodePrettily(updatedRecipe));
            } else {
              routingContext.fail(500);
            }
          });
      } else {
        routingContext.fail(conn.cause());
      }
    });
  }

  // Handler for deleting a recipe
  private void deleteRecipe(RoutingContext routingContext) {
    String id = routingContext.request().getParam("id");

    client.getConnection(conn -> {
      if (conn.succeeded()) {
        SqlConnection connection = conn.result();
        connection.preparedQuery("DELETE FROM recipes WHERE id = ?")
          .execute(Tuple.of(Integer.parseInt(id)), ar -> {
            connection.close();
            if (ar.succeeded()) {
              routingContext.response().setStatusCode(204).end();
            } else {
              routingContext.fail(500);
            }
          });
      } else {
        routingContext.fail(conn.cause());
      }
    });
  }


  }
