package com.example.starter;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;

import java.util.ArrayList;
import java.util.List;

public class MainVerticle extends AbstractVerticle {

  @Override
  public void start(Promise<Void> startPromise) throws Exception {

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

  private void addRecipe(RoutingContext routingContext){
    JsonObject body = routingContext.getBodyAsJson();
    Rezept rezept = body.mapTo(Rezept.class);

    System.out.println("Added recipe: "+rezept.getTitle());

    routingContext.response()
      .setStatusCode(201)
      .putHeader("content-type", "application/json")
      .end(io.vertx.core.json.Json.encodePrettily(rezept));
  }

  private void getAllRecipes(RoutingContext routingContext){
    List<Rezept> recipes = new ArrayList<>();

    routingContext.response()
      .setStatusCode(200)
      .putHeader("content-type", "application/json")
      .end(io.vertx.core.json.Json.encodePrettily(recipes));
  }
  private void getRecipesById(RoutingContext routingContext) {
    // Fetch the recipe by ID (e.g., from a database or in-memory list)
    String id = routingContext.request().getParam("id");

    // For now, return a dummy recipe
    Rezept rezept = new Rezept();
    rezept.setId(id);
    rezept.setTitle("Sample Recipe");

    // Respond with the recipe
    routingContext.response()
      .setStatusCode(200)
      .putHeader("content-type", "application/json")
      .end(io.vertx.core.json.Json.encodePrettily(rezept));
  }

  // Handler for updating a recipe
  private void updateRecipe(io.vertx.ext.web.RoutingContext routingContext) {
    // Update the recipe by ID
    String id = routingContext.request().getParam("id");
    io.vertx.core.json.JsonObject body = routingContext.getBodyAsJson();
    Rezept updatedRecipe = body.mapTo(Rezept.class);

    // For now, just print the updated recipe
    System.out.println("Updated recipe: " + updatedRecipe.getTitle());

    // Respond with the updated recipe
    routingContext.response()
      .setStatusCode(200)
      .putHeader("content-type", "application/json")
      .end(io.vertx.core.json.Json.encodePrettily(updatedRecipe));
  }

  // Handler for deleting a recipe
  private void deleteRecipe(io.vertx.ext.web.RoutingContext routingContext) {
    // Delete the recipe by ID
    String id = routingContext.request().getParam("id");

    // For now, just print the deleted recipe ID
    System.out.println("Deleted recipe with ID: " + id);

    // Respond with a success message
    routingContext.response()
      .setStatusCode(204)
      .end();
  }


  }
