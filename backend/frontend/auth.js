// Login Function
async function login() {
  const email = document.getElementById("email").value;
  const password = document.getElementById("password").value;

  // Check if email and password are filled
  if (!email || !password) {
    document.getElementById("message").textContent = "Bitte alle Felder ausfüllen!";
    return;
  }

  try {
    // Send login request to the backend
    const response = await fetch("http://localhost:8080/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password })
    });

    const result = await response.json();

    // Check if the response is successful
    if (response.ok) {
      // Validate the response data
      if (!result.token || !result.userId) {
        throw new Error("Antwort vom Server ist ungültig! `userId` oder `token` fehlt.");
      }

      // Store token and user ID in localStorage
      localStorage.setItem("token", result.token);
      localStorage.setItem("userId", result.userId);

      console.log(`🔑 Erfolgreich eingeloggt! userId: ${result.userId}`);

      // Redirect to the home page
      window.location.href = "recipes.html"; // Updated to match your file structure
    } else {
      // Handle login failure
      throw new Error(result.message || "Login fehlgeschlagen!");
    }
  } catch (error) {
    // Handle errors
    console.error("❌ Fehler beim Login:", error);
    document.getElementById("message").textContent = error.message;
  }
}

// Registration Function
async function register() {
  const username = document.getElementById("username").value;
  const email = document.getElementById("email").value;
  const password = document.getElementById("password").value;

  // Check if all fields are filled
  if (!username || !email || !password) {
    document.getElementById("message").textContent = "Bitte alle Felder ausfüllen!";
    return;
  }

  try {
    // Send registration request to the backend
    const response = await fetch("http://localhost:8080/register", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, email, password })
    });

    const result = await response.json();

    // Check if the response is successful
    if (response.ok) {
      console.log("✅ Erfolgreich registriert!");
      // Redirect to the login page after successful registration
      window.location.href = "login.html";
    } else {
      throw new Error(result.message || "Registrierung fehlgeschlagen!");
    }
  } catch (error) {
    // Handle errors
    console.error("❌ Fehler bei der Registrierung:", error);
    document.getElementById("message").textContent = error.message;
  }
}

// Logout Function
function logout() {
  // Remove token and user ID from localStorage
  localStorage.removeItem("token");
  localStorage.removeItem("userId");

  // Redirect to the login page
  window.location.href = "login.html";
}

// Optional: Event Listener for the Enter Key
document.addEventListener("keypress", function (event) {
  if (event.key === "Enter") {
    if (window.location.pathname.endsWith("login.html")) {
      login();
    } else if (window.location.pathname.endsWith("register.html")) {
      register();
    }
  }
});

// Add event listeners for login and register buttons
document.addEventListener("DOMContentLoaded", () => {
  const loginButton = document.getElementById("loginButton");
  if (loginButton) {
    loginButton.addEventListener("click", login);
  }

  const registerButton = document.getElementById("registerButton");
  if (registerButton) {
    registerButton.addEventListener("click", register);
  }

  const logoutLink = document.querySelector('a[href="logout"]');
  if (logoutLink) {
    logoutLink.addEventListener("click", (e) => {
      e.preventDefault();
      logout();
    });
  }
});
