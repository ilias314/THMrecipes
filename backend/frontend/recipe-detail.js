const API_URL = "http://localhost:8080";

document.addEventListener("DOMContentLoaded", () => {
  const urlParams = new URLSearchParams(window.location.search);
  const recipeId = urlParams.get("id");

  if (recipeId) {
    fetchRecipeDetails(recipeId); // Fetch recipe details
    fetchComments(recipeId);     // Fetch comments
    fetchRatings(recipeId);      // Fetch ratings

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
  } else {
    console.error("❌ Recipe ID not found in URL.");
  }
});

// Fetch recipe details
async function fetchRecipeDetails(recipeId) {
  const token = localStorage.getItem("token");

  if (!token) {
    alert("Please log in first!");
    window.location.href = "login.html";
    return;
  }

  try {
    const response = await fetch(`${API_URL}/recipes/${recipeId}`, {
      method: "GET",
      headers: {
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      }
    });

    if (response.ok) {
      const recipe = await response.json();
      displayRecipeDetails(recipe);
    } else {
      const errorData = await response.json();
      throw new Error(errorData.message || "Failed to fetch recipe details.");
    }
  } catch (error) {
    console.error("❌ Error fetching recipe details:", error);
    alert(error.message);
  }
}

// Display recipe details
function displayRecipeDetails(recipe) {
  const recipeTitle = document.getElementById("recipeTitle");
  const recipeDescription = document.getElementById("recipeDescription");
  const ingredientsList = document.getElementById("ingredientsList");
  const instructionsList = document.getElementById("instructionsList");

  // Set recipe title and description
  recipeTitle.textContent = recipe.title;
  recipeDescription.textContent = recipe.description;

  // Clear existing lists
  ingredientsList.innerHTML = "";
  instructionsList.innerHTML = "";

  // Add ingredients to the list
  if (Array.isArray(recipe.ingredients)) {
    recipe.ingredients.forEach(ingredient => {
      const li = document.createElement("li");
      li.textContent = ingredient;
      ingredientsList.appendChild(li);
    });
  } else {
    console.warn("⚠ Ingredients is not an array:", recipe.ingredients);
  }

  // Add instructions to the list
  if (Array.isArray(recipe.instructions)) {
    recipe.instructions.forEach(instruction => {
      const li = document.createElement("li");
      li.textContent = instruction;
      instructionsList.appendChild(li);
    });
  } else {
    console.warn("⚠ Instructions is not an array:", recipe.instructions);
  }
}

// Add a comment
async function addComment(recipeId, content) {
  const token = localStorage.getItem("token");

  if (!token) {
    alert("Please log in first!");
    window.location.href = "login.html";
    return;
  }

  try {
    const response = await fetch(`${API_URL}/recipes/${recipeId}/comments`, {
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
      document.getElementById("comment").value = ""; // Clear the comment input
    } else {
      const errorData = await response.json();
      throw new Error(errorData.message || "Failed to add comment.");
    }
  } catch (error) {
    console.error("❌ Error adding comment:", error);
    alert(error.message);
  }
}

// Fetch comments
async function fetchComments(recipeId) {
  const token = localStorage.getItem("token");

  try {
    const response = await fetch(`${API_URL}/recipes/${recipeId}/comments`, {
      method: "GET",
      headers: {
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      }
    });

    if (response.ok) {
      let comments = await response.json();

      // Ensure comments is an array
      if (!Array.isArray(comments)) {
        console.warn("⚠ Comments is not an array:", comments);
        comments = []; // Default to an empty array
      }

      displayComments(comments);
    } else {
      const errorData = await response.json();
      throw new Error(errorData.message || "Failed to fetch comments.");
    }
  } catch (error) {
    console.error("❌ Error fetching comments:", error);
    alert(error.message);
  }
}

// Display comments
function displayComments(comments) {
  const commentsList = document.getElementById("commentsList");
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

// Add a rating
async function addRating(recipeId, rating) {
  const token = localStorage.getItem("token");

  if (!token) {
    alert("Please log in first!");
    window.location.href = "login.html";
    return;
  }

  try {
    const response = await fetch(`${API_URL}/recipes/${recipeId}/ratings`, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ rating: parseInt(rating, 10) })
    });

    if (response.ok) {
      // Refresh the ratings list
      fetchRatings(recipeId);
    } else {
      const errorData = await response.json();
      throw new Error(errorData.message || "Failed to add rating.");
    }
  } catch (error) {
    console.error("❌ Error adding rating:", error);
    alert(error.message);
  }
}

// Fetch ratings
async function fetchRatings(recipeId) {
  const token = localStorage.getItem("token");

  try {
    const response = await fetch(`${API_URL}/recipes/${recipeId}/ratings`, {
      method: "GET",
      headers: {
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      }
    });

    if (response.ok) {
      let ratings = await response.json();

      // Ensure ratings is an array
      if (!Array.isArray(ratings)) {
        console.warn("⚠ Ratings is not an array:", ratings);
        ratings = []; // Default to an empty array
      }

      displayRatings(ratings);
    } else {
      const errorData = await response.json();
      throw new Error(errorData.message || "Failed to fetch ratings.");
    }
  } catch (error) {
    console.error("❌ Error fetching ratings:", error);
    alert(error.message);
  }
}

// Display ratings
function displayRatings(ratings) {
  const ratingsList = document.getElementById("ratingsList");
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
