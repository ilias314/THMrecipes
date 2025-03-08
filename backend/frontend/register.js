const API_URL = "http://localhost:8080";

async function register() {
  const username = document.getElementById("username").value.trim();
  const email = document.getElementById("email").value.trim();
  const password = document.getElementById("password").value.trim();
  const messageBox = document.getElementById("message");

  if (!username || !email || !password) {
    messageBox.textContent = "❌ Please fill in all fields!";
    messageBox.classList.add("text-danger");
    return;
  }

  try {
    const response = await fetch(`${API_URL}/register`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, email, password }),
    });

    const result = await response.json();

    if (response.ok) {
      messageBox.textContent = "✅ Registration successful! Redirecting to login...";
      messageBox.classList.remove("text-danger");
      messageBox.classList.add("text-success");

      setTimeout(() => {
        window.location.href = "login.html"; // Redirect after success
      }, 1500);
    } else {
      throw new Error(result.message || "Registration failed!");
    }
  } catch (error) {
    console.error("❌ Registration error:", error);
    messageBox.textContent = `❌ ${error.message}`;
    messageBox.classList.add("text-danger");
  }
}
