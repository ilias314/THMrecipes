/**
 * @file wishlist.js
 * @description Verwalten der Wunschliste eines Benutzers: Anzeigen, Hinzufügen und Entfernen von Rezepten.
 */

const API_URL = "http://localhost:8080";

document.addEventListener("DOMContentLoaded", () => {
  loadWishlist();
});

/**
 * Zeigt eine Bootstrap-Alert-Meldung an.
 * @param {string} message - Die Nachricht, die angezeigt wird.
 * @param {string} type - Bootstrap-Alert-Typ (info, success, warning, danger).
 * @param {number} timeout - Dauer in Millisekunden, bevor die Meldung automatisch verschwindet.
 */
function showAlert(message, type = "info", timeout = 3000) {
  const alertContainer = document.getElementById("alertContainer");
  if (!alertContainer) return;

  const alert = document.createElement("div");
  alert.className = `alert alert-${type} alert-dismissible fade show`;
  alert.role = "alert";
  alert.innerHTML = `
        ${message}
        <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
    `;

  alertContainer.appendChild(alert);

  setTimeout(() => {
    alert.classList.remove("show");
    alert.classList.add("fade");
    setTimeout(() => alert.remove(), 500);
  }, timeout);
}

/**
 * @api {get} /wishlist/:userId Lade die Wunschliste eines Benutzers
 * @apiName GetWishlist
 * @apiGroup Wishlist
 * @apiHeader {String} Authorization Bearer Token
 * @apiSuccess {Object[]} wishlist Array von Rezept-Objekten in der Wunschliste.
 * @apiError 401 Unauthorized Kein Zugriff, Token fehlt oder ungültig.
 * @apiError 500 Internal Server Error Fehler beim Abrufen der Daten.
 */
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
    showAlert("Error loading wishlist.", "danger");
  }
}

/**
 * Zeigt die Wunschliste auf der Seite an.
 * @param {Object[]} wishlist - Liste der Rezeptobjekte in der Wunschliste.
 */
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

/**
 * @api {delete} /wishlist/:userId/:recipeId Rezept aus der Wunschliste entfernen
 * @apiName RemoveFromWishlist
 * @apiGroup Wishlist
 * @apiHeader {String} Authorization Bearer Token
 * @apiParam {Number} userId ID des Benutzers
 * @apiParam {Number} recipeId ID des zu entfernenden Rezepts
 * @apiSuccess 200 OK Rezept erfolgreich entfernt
 * @apiError 401 Unauthorized Kein Zugriff, Token fehlt oder ungültig
 * @apiError 404 Not Found Rezept nicht gefunden oder nicht in der Wunschliste
 * @apiError 500 Internal Server Error Fehler beim Entfernen des Rezepts
 */
async function removeFromWishlist(recipeId) {
  const token = localStorage.getItem("accessToken");
  const userId = localStorage.getItem("userId");

  if (!token || !userId) {
    showAlert("Please log in first!", "warning");
    return;
  }

  try {
    const response = await fetch(`${API_URL}/wishlist/${userId}/${recipeId}`, {
      method: "DELETE",
      headers: { "Authorization": `Bearer ${token}` }
    });

    if (response.ok) {
      showAlert("Removed from wishlist!", "success");
      loadWishlist();
    } else if (response.status === 404) {
      showAlert("Recipe not found in wishlist.", "warning");
    } else {
      throw new Error("Failed to remove from wishlist.");
    }
  } catch (error) {
    console.error("Error removing from wishlist:", error);
    showAlert("Error removing from wishlist.", "danger");
  }
}

/**
 * Loggt den Benutzer aus, löscht das Token und leitet zur Login-Seite weiter.
 */
function logout() {
  localStorage.removeItem("accessToken");
  localStorage.removeItem("userId");
  showAlert("Successfully logged out!", "info");
  setTimeout(() => window.location.href = "login.html", 1000);
}
