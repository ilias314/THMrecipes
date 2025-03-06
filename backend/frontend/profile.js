// Function to fetch user profile
async function fetchProfile() {
  const token = localStorage.getItem("token");

  // Check if the user is logged in
  if (!token) {
    alert("Bitte zuerst einloggen!");
    window.location.href = "login.html";
    return;
  }

  const userId = localStorage.getItem("userId");

  if (!userId) {
    alert("Benutzer-ID fehlt!");
    return;
  }

  try {
    // Fetch user profile from the backend
    const profileResponse = await fetch(`http://localhost:8080/users/${userId}`, {
      method: "GET",
      headers: {
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      }
    });

    // Check if the response is successful
    if (profileResponse.ok) {
      const user = await profileResponse.json();
      displayProfile(user);
    } else {
      const errorData = await profileResponse.json();
      throw new Error(errorData.message || "Fehler beim Abrufen des Profils.");
    }

    // Fetch user recipes
    const recipesResponse = await fetch(`http://localhost:8080/users/${userId}/recipes`, {
      method: "GET",
      headers: {
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      }
    });

    if (recipesResponse.ok) {
      const recipes = await recipesResponse.json();
      displayUserRecipes(recipes);
    } else {
      throw new Error("Fehler beim Abrufen der Benutzerrezepte.");
    }

    // Fetch user comments
    const commentsResponse = await fetch(`http://localhost:8080/users/${userId}/comments`, {
      method: "GET",
      headers: {
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      }
    });

    if (commentsResponse.ok) {
      const comments = await commentsResponse.json();
      displayUserComments(comments);
    } else {
      throw new Error("Fehler beim Abrufen der Benutzerkommentare.");
    }

    // Fetch user ratings
    const ratingsResponse = await fetch(`http://localhost:8080/users/${userId}/ratings`, {
      method: "GET",
      headers: {
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      }
    });

    if (ratingsResponse.ok) {
      const ratings = await ratingsResponse.json();
      displayUserRatings(ratings);
    } else {
      throw new Error("Fehler beim Abrufen der Benutzerbewertungen.");
    }
  } catch (error) {
    console.error("❌ Fehler:", error);
    alert(error.message);
  }
}

// Function to display user profile
function displayProfile(user) {
  const profileInfo = document.getElementById("profileInfo");

  // Clear the profile info before adding new data
  profileInfo.innerHTML = "";

  // Add user profile data
  profileInfo.innerHTML = `
    <p>Username: ${user.username}</p>
    <p>Email: ${user.email}</p>
  `;
}

// Function to display user recipes
function displayUserRecipes(recipes) {
  const userRecipes = document.getElementById("userRecipes");

  // Clear the list before adding new recipes
  userRecipes.innerHTML = "";

  // Add each recipe to the list
  recipes.forEach(recipe => {
    const recipeDiv = document.createElement("div");
    recipeDiv.className = "recipe";
    recipeDiv.innerHTML = `
      <h3>${recipe.title}</h3>
      <p>${recipe.description}</p>
      <a href="recipe-detail.html?id=${recipe.id}">View Recipe</a>
    `;
    userRecipes.appendChild(recipeDiv);
  });
}

// Function to display user comments
function displayUserComments(comments) {
  const userComments = document.getElementById("userComments");

  // Clear the list before adding new comments
  userComments.innerHTML = "";

  // Add each comment to the list
  comments.forEach(comment => {
    const commentDiv = document.createElement("div");
    commentDiv.className = "comment";
    commentDiv.innerHTML = `
      <h3>${comment.recipeTitle}</h3>
      <p>${comment.content}</p>
      <p>${comment.createdAt}</p>
    `;
    userComments.appendChild(commentDiv);
  });
}

// Function to display user ratings
function displayUserRatings(ratings) {
  const userRatings = document.getElementById("userRatings");

  // Clear the list before adding new ratings
  userRatings.innerHTML = "";

  // Add each rating to the list
  ratings.forEach(rating => {
    const ratingDiv = document.createElement("div");
    ratingDiv.className = "rating";
    ratingDiv.innerHTML = `
      <h3>${rating.recipeTitle}</h3>
      <p>Rating: ${rating.rating}</p>
      <p>${rating.createdAt}</p>
    `;
    userRatings.appendChild(ratingDiv);
  });
}

// Fetch profile, recipes, comments, and ratings when the page loads
document.addEventListener("DOMContentLoaded", fetchProfile);
