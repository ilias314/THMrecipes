const API_URL = "http://localhost:8880";

async function login() {
  const email = document.getElementById("email").value;
  const password = document.getElementById("password").value;

  if (!email || !password) {
    showCustomToast("Bitte alle Felder ausfüllen!", "danger");
    return;
  }

  try {
    const response = await fetch(`${API_URL}/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password })
    });

    const result = await response.json();

    if (response.ok) {
      localStorage.setItem("accessToken", result.accessToken);
      localStorage.setItem("refreshToken", result.refreshToken);
      localStorage.setItem("userId", result.userId);

      console.log(`Erfolgreich eingeloggt! userId: ${result.userId}`);
      window.location.href = "recipes.html";  // Redirect to recipes page
    } else {
      throw new Error(result.message || "Login fehlgeschlagen!");
    }
  } catch (error) {
    console.error("Fehler beim Login:", error);
    showCustomToast(error.message, "danger");
  }
}

// Custom toast notification function
function showCustomToast(message, type = "success") {
  const container = document.getElementById("toastContainer");
  if (!container) return;
  const toast = document.createElement("div");
  toast.classList.add("toast-custom", type);
  toast.textContent = message;
  container.appendChild(toast);
  setTimeout(() => { toast.classList.add("show"); }, 100);
  setTimeout(() => {
    toast.classList.remove("show");
    setTimeout(() => { container.removeChild(toast); }, 500);
  }, 3000);
}

// Example: Fetching user data using the new system
async function getUserData() {
  try {
    const response = await fetchWithAuth(`${API_URL}/users`);
    if (!response.ok) throw new Error("Failed to fetch user data");
    const data = await response.json();
    console.log("User Data:", data);
  } catch (error) {
    console.error("Error:", error);
  }
}
