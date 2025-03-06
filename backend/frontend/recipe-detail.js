// Function to fetch recipe details
async function fetchRecipeDetails() {
  const token = localStorage.getItem("token");

  // Check if the user is logged in
  if (!token) {
    alert("Bitte zuerst einloggen!");
    window.location.href = "login.html";
    return;
  }

  // Get the recipe ID from the URL
  const urlParams = new URLSearchParams(window.location.search);
  const recipeId = urlParams.get("id");

  if (!recipeId) {
    alert("Rezept-ID fehlt!");
    return;
  }

  try {
    // Fetch recipe details from the backend
    const response = await fetch(`http://localhost:8080/recipes/${recipeId}`, {
      method: "GET",
      headers: {
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      }
    });

    // Check if the response is successful
    if (response.ok) {
      const recipe = await response.json();
      displayRecipeDetails(recipe);
    } else {
      const errorData = await response.json();
      throw new Error(errorData.message || "Fehler beim Abrufen der Rezeptdetails.");
    }
  } catch (error) {
    console.error("❌ Fehler beim Abrufen der Rezeptdetails:", error);
    alert(error.message);
  }
}

// Function to display recipe details
function displayRecipeDetails(recipe) {
  const recipeTitle = document.getElementById("recipeTitle");
  const recipeDescription = document.getElementById("recipeDescription");
  const ingredientsList = document.getElementById("ingredientsList");
  const instructionsList = document.getElementById("instructionsList");

  // Set the recipe title and description
  recipeTitle.textContent = recipe.title;
  recipeDescription.textContent = recipe.description;

  // Clear the lists before adding new items
  ingredientsList.innerHTML = "";
  instructionsList.innerHTML = "";

  // Add each ingredient to the list
  recipe.ingredients.forEach(ingredient => {
    const li = document.createElement("li");
    li.textContent = ingredient;
    ingredientsList.appendChild(li);
  });

  // Add each instruction to the list
  recipe.instructions.forEach(instruction => {
    const li = document.createElement("li");
    li.textContent = instruction;
    instructionsList.appendChild(li);
  });
}

// Function to add a comment
async function addComment(recipeId, content) {
  const token = localStorage.getItem("token");

  try {
    const response = await fetch(`http://localhost:8080/recipes/${recipeId}/comments`, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ content })
    });

    if (response.ok) {
      // Refresh the comments list
      fetchComments(recipeId);
    } else {
      const errorData = await response.json();
      throw new Error(errorData.message || "Fehler beim Hinzufügen des Kommentars.");
    }
  } catch (error) {
    console.error("❌ Fehler beim Hinzufügen des Kommentars:", error);
    alert(error.message);
  }
}

// Function to fetch comments
async function fetchComments(recipeId) {
  const token = localStorage.getItem("token");

  try {
    const response = await fetch(`http://localhost:8080/recipes/${recipeId}/comments`, {
      method: "GET",
      headers: {
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      }
    });

    if (response.ok) {
      const comments = await response.json();
      displayComments(comments);
    } else {
      const errorData = await response.json();
      throw new Error(errorData.message || "Fehler beim Abrufen der Kommentare.");
    }
  } catch (error) {
    console.error("❌ Fehler beim Abrufen der Kommentare:", error);
    alert(error.message);
  }
}

// Function to display comments
function displayComments(comments) {
  const commentsList = document.getElementById("commentsList");

  // Clear the list before adding new comments
  commentsList.innerHTML = "";

  // Add each comment to the list
  comments.forEach(comment => {
    const commentDiv = document.createElement("div");
    commentDiv.className = "comment";
    commentDiv.innerHTML = `
      <h3>${comment.username}</h3>
      <p>${comment.content}</p>
      <p>${comment.createdAt}</p>
    `;
    commentsList.appendChild(commentDiv);
  });
}

// Function to add a rating
async function addRating(recipeId, rating) {
  const token = localStorage.getItem("token");

  try {
    // Convert the rating to an integer
    const ratingValue = parseInt(rating, 10);

    const response = await fetch(`http://localhost:8080/recipes/${recipeId}/ratings`, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ rating: ratingValue }) // Send as a number
    });

    if (response.ok) {
      // Refresh the ratings list
      fetchRatings(recipeId);
    } else {
      const errorData = await response.json();
      throw new Error(errorData.message || "Fehler beim Hinzufügen der Bewertung.");
    }
  } catch (error) {
    console.error("❌ Fehler beim Hinzufügen der Bewertung:", error);
    alert(error.message);
  }
}

// Function to fetch ratings
async function fetchRatings(recipeId) {
  const token = localStorage.getItem("token");

  try {
    const response = await fetch(`http://localhost:8080/recipes/${recipeId}/ratings`, {
      method: "GET",
      headers: {
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      }
    });

    if (response.ok) {
      const ratings = await response.json();
      displayRatings(ratings);
    } else {
      const errorData = await response.json();
      throw new Error(errorData.message || "Fehler beim Abrufen der Bewertungen.");
    }
  } catch (error) {
    console.error("❌ Fehler beim Abrufen der Bewertungen:", error);
    alert(error.message);
  }
}

// Function to display ratings
function displayRatings(ratings) {
  const ratingsList = document.getElementById("ratingsList");

  // Clear the list before adding new ratings
  ratingsList.innerHTML = "";

  // Add each rating to the list
  ratings.forEach(rating => {
    const ratingDiv = document.createElement("div");
    ratingDiv.className = "rating";
    ratingDiv.innerHTML = `
      <h3>${rating.username}</h3>
      <p>Rating: ${rating.rating}</p>
      <p>${rating.createdAt}</p>
    `;
    ratingsList.appendChild(ratingDiv);
  });
}

// Fetch recipe details, comments, and ratings when the page loads
document.addEventListener("DOMContentLoaded", () => {
  const urlParams = new URLSearchParams(window.location.search);
  const recipeId = urlParams.get("id");

  if (recipeId) {
    fetchRecipeDetails(recipeId);
    fetchComments(recipeId);
    fetchRatings(recipeId);

    // Add event listener for the comment form
    const commentForm = document.getElementById("commentForm");
    if (commentForm) {
      commentForm.addEventListener("submit", (e) => {
        e.preventDefault();
        const content = document.getElementById("comment").value;
        addComment(recipeId, content);
      });
    }

    // Add event listener for the rating form
    const ratingForm = document.getElementById("ratingForm");
    if (ratingForm) {
      ratingForm.addEventListener("submit", (e) => {
        e.preventDefault();
        const rating = document.getElementById("rating").value;
        addRating(recipeId, rating);
      });
    }
  }
});
