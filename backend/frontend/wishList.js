/**
 * @file wishList.js
 * @description Manages the user's wishlist: displaying, adding, and removing recipes.
 */

const API_URL = "http://localhost:8080";

document.addEventListener("DOMContentLoaded", () => {
  loadWishlist();
});

async function loadWishlist() {
  const token = localStorage.getItem("accessToken");
  const userId = localStorage.getItem("userId");

  if (!token || !userId) {
    window.location.href = "login.html";
    return;
  }

  try {
    const response = await fetch(`${API_URL}/wishlist/${userId}`, {
      method: "GET",
      headers: { "Authorization": `Bearer ${token}` }
    });

    if (!response.ok) throw new Error("Failed to fetch wishlist");

    const wishlist = await response.json();
    displayWishlist(wishlist);
  } catch (error) {
    console.error("Error loading wishlist:", error);
    showCustomToast("Error loading wishlist.", "danger");
  }
}

function displayWishlist(wishlist) {
  const wishlistContainer = document.getElementById("wishlistContainer");
  wishlistContainer.innerHTML = "";

  if (wishlist.length === 0) {
    wishlistContainer.innerHTML = "<p class='text-center'>No recipes in your wishlist.</p>";
    return;
  }

  wishlist.forEach(recipe => {
    let imageUrl = recipe.image_url && (recipe.image_url.startsWith("http") || recipe.image_url.startsWith("/"))
      ? recipe.image_url
      : "default-image.jpg";

    wishlistContainer.innerHTML += `
        <div class="col-md-4 mb-4">
          <div class="card shadow-sm">
            <img src="${imageUrl}" class="card-img-top" alt="${recipe.title}" onerror="this.src='default-image.jpg';">
            <div class="card-body">
              <h5 class="card-title">${recipe.title}</h5>
              <p class="card-text">${recipe.description || ''}</p>
              <a href="recipe-detail.html?id=${recipe.id}" class="btn btn-primary mb-2">
                <i class="fas fa-eye"></i> View
              </a>
              <button class="btn btn-danger" onclick="removeFromWishlist(${recipe.id})">
                <i class="fas fa-trash"></i>
              </button>
            </div>
          </div>
        </div>`;
  });
}

async function removeFromWishlist(recipeId) {
  const token = localStorage.getItem("accessToken");
  const userId = localStorage.getItem("userId");

  if (!token || !userId) {
    showCustomToast("Please log in first!", "warning");
    return;
  }

  try {
    const response = await fetch(`${API_URL}/wishlist/${userId}/${recipeId}`, {
      method: "DELETE",
      headers: { "Authorization": `Bearer ${token}` }
    });

    if (response.ok) {
      showCustomToast("Removed from wishlist!", "success");
      loadWishlist();
    } else if (response.status === 404) {
      showCustomToast("Recipe not found in wishlist.", "warning");
    } else {
      throw new Error("Failed to remove from wishlist.");
    }
  } catch (error) {
    console.error("Error removing from wishlist:", error);
    showCustomToast("Error removing from wishlist.", "danger");
  }
}

function logout() {
  localStorage.removeItem("accessToken");
  localStorage.removeItem("userId");
  showCustomToast("Successfully logged out!", "info");
  setTimeout(() => window.location.href = "login.html", 1000);
}

function showCustomToast(message, type = "success") {
  const container = document.getElementById("toastContainer");
  if (!container) return;
  const toast = document.createElement("div");
  toast.classList.add("toast-custom", type);
  toast.textContent = message;
  container.appendChild(toast);
  setTimeout(() => toast.classList.add("show"), 100);
  setTimeout(() => {
    toast.classList.remove("show");
    setTimeout(() => container.removeChild(toast), 500);
  }, 3000);
}
