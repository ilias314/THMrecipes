const API_URL = "http://localhost:8080";

document.addEventListener("DOMContentLoaded", () => {
  const token = localStorage.getItem("token");

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

      const title = document.getElementById("title").value.trim();
      const description = document.getElementById("description").value.trim();
      const ingredients = document.getElementById("ingredients").value.split(",").map(item => item.trim());
      const instructions = document.getElementById("instructions").value.split("\n").map(item => item.trim());
      const imageFile = document.getElementById("imageUpload").files[0];

      if (!imageFile) {
        console.error("❌ No image selected!");
        showToast("❌ Please select an image.", "danger");
        submitButton.disabled = false;
        hideLoadingSpinner();
        return;
      }

      console.log("📸 Selected image:", imageFile);

      try {
        // Step 1: Upload the Image
        const imageFormData = new FormData();
        imageFormData.append("image", imageFile);

        const imageResponse = await fetch(`${API_URL}/upload`, {
          method: "POST",
          body: imageFormData,
        });

        const imageResult = await imageResponse.json();
        console.log("📤 Image upload response:", imageResult);

        if (!imageResponse.ok || !imageResult.image_url) {
          console.error("❌ Image upload failed:", imageResult);
          showToast(`❌ Image upload failed: ${JSON.stringify(imageResult)}`, "danger");
          submitButton.disabled = false;
          hideLoadingSpinner();
          return;
        }

        const imageUrl = imageResult.image_url;
        console.log("🖼️ Image URL received:", imageUrl);

        // Step 2: Send Recipe Data to Backend
        const recipeData = {
          title,
          description,
          ingredients,
          instructions,
          imageUrl, // Using the received image URL
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
          }, 2000);
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

// 🌀 Show Loading Spinner
function showLoadingSpinner() {
  document.getElementById("message").innerHTML = `
    <div class="spinner-border text-primary" role="status">
      <span class="visually-hidden">Loading...</span>
    </div>
  `;
}

// ❌ Hide Loading Spinner
function hideLoadingSpinner() {
  document.getElementById("message").innerHTML = "";
}

// 🔔 Show Toast Notification
function showToast(message, type = "success") {
  const toast = document.getElementById("toast");
  const toastBody = toast.querySelector(".toast-body");

  toastBody.textContent = message;
  toast.classList.remove("bg-success", "bg-danger");
  toast.classList.add(`bg-${type}`);

  const toastInstance = new bootstrap.Toast(toast);
  toastInstance.show();
}
