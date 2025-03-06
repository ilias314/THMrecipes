// Function to fetch all recipes
async function fetchRecipes() {
  const token = localStorage.getItem("token");

  // Check if the user is logged in
  if (!token) {
    alert("Bitte zuerst einloggen!");
    window.location.href = "login.html";
    return;
  }

  try {
    // Fetch recipes from the backend
    const response = await fetch("http://localhost:8080/recipes", {
      method: "GET",
      headers: {
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      }
    });

    // Check if the response is successful
    if (response.ok) {
      const recipes = await response.json();
      displayRecipes(recipes);
    } else {
      const errorData = await response.json();
      throw new Error(errorData.message || "Fehler beim Abrufen der Rezepte.");
    }
  } catch (error) {
    console.error("❌ Fehler beim Abrufen der Rezepte:", error);
    alert(error.message);
  }
}

// Function to display recipes
function displayRecipes(recipes) {
  const recipesList = document.getElementById("recipesList");

  // Clear the list before adding new recipes
  recipesList.innerHTML = "";

  // Add each recipe to the list
  recipes.forEach(recipe => {
    const recipeDiv = document.createElement("div");
    recipeDiv.className = "recipe";
    recipeDiv.innerHTML = `
      <h3>${recipe.title}</h3>
      <p>${recipe.description}</p>
      <a href="recipe-detail.html?id=${recipe.id}">View Recipe</a>
    `;
    recipesList.appendChild(recipeDiv);
  });
}

// Fetch recipes when the page loads
document.addEventListener("DOMContentLoaded", fetchRecipes);
