// add-recipe.js
const API_URL = "http://localhost:8080";

document.addEventListener("DOMContentLoaded", () => {
  // Check for a valid token in localStorage
  const token = localStorage.getItem("accessToken");
  if (!token) {
    alert("Please log in first!");
    window.location.href = "login.html";
    return;
  }

  const form = document.getElementById("addRecipeForm");
  if (form) {
    form.addEventListener("submit", async (event) => {
      event.preventDefault();

      const submitButton = form.querySelector("button[type='submit']");
      submitButton.disabled = true;
      showLoadingSpinner();

      // Get values from form inputs
      const title = document.getElementById("title").value.trim();
      const description = document.getElementById("description").value.trim();
      const ingredients = document.getElementById("ingredients").value.trim(); // ✅ Now stored as plain text
      const instructions = document.getElementById("instructions").value.trim(); // ✅ No auto-splitting, stored as plain text
      const imageFile = document.getElementById("imageUpload").files[0];

      if (!title || !description || !ingredients || !instructions) {
        showToast("❌ Please fill in all fields.", "danger");
        submitButton.disabled = false;
        hideLoadingSpinner();
        return;
      }

      let imageUrl = "/images/default.png"; // Default image in case upload fails

      try {
        if (imageFile) {
          // 1) Upload Image
          const imageFormData = new FormData();
          imageFormData.append("image", imageFile);

          const imageResponse = await fetch(`${API_URL}/upload`, {
            method: "POST",
            body: imageFormData,
          });

          const imageResult = await imageResponse.json();
          console.log("📤 Image upload response:", imageResult);

          if (imageResponse.ok && imageResult.image_url) {
            imageUrl = imageResult.image_url;
          } else {
            console.error("❌ Image upload failed:", imageResult);
            showToast(`⚠️ Image upload failed. Using default image.`, "warning");
          }
        }

        // 2) Send Recipe Data to Backend
        const recipeData = {
          title,
          description,
          ingredients,  // ✅ Now stored as plain text, no list/array
          instructions, // ✅ No formatting, stored exactly as user types
          image_url: imageUrl, // ✅ Use uploaded image or fallback to default
        };

        console.log("📜 Sending recipe data:", recipeData);

        const recipeResponse = await fetch(`${API_URL}/recipes`, {
          method: "POST",
          headers: {
            "Authorization": `Bearer ${token}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify(recipeData),
        });

        const recipeResult = await recipeResponse.json();
        console.log("✅ Recipe creation response:", recipeResult);

        if (recipeResponse.ok) {
          showToast("✅ Recipe added successfully!", "success");
          setTimeout(() => {
            window.location.href = "recipes.html"; // Redirect to recipes page
          }, 1500);
        } else {
          console.error("❌ Recipe creation failed:", recipeResult);
          showToast(`❌ Failed to add recipe: ${recipeResult.message}`, "danger");
        }
      } catch (error) {
        console.error("❌ Error in submission:", error);
        showToast("❌ Something went wrong!", "danger");
      } finally {
        hideLoadingSpinner();
        submitButton.disabled = false;
      }
    });
  }
});

// Show a loading spinner
function showLoadingSpinner() {
  document.getElementById("message").innerHTML = `
    <div class="spinner-border text-primary" role="status">
      <span class="visually-hidden">Loading...</span>
    </div>
  `;
}

// Hide the loading spinner
function hideLoadingSpinner() {
  document.getElementById("message").innerHTML = "";
}

// Show a toast notification
function showToast(message, type = "success") {
  const toast = document.getElementById("toast");
  const toastBody = toast.querySelector(".toast-body");

  toastBody.textContent = message;
  toast.classList.remove("bg-success", "bg-danger", "bg-warning");
  toast.classList.add(`bg-${type}`);

  const toastInstance = new bootstrap.Toast(toast);
  toastInstance.show();
}
