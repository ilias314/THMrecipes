const API_URL = "http://localhost:8080";

// Login Function
async function login() {
  const email = document.getElementById("email").value;
  const password = document.getElementById("password").value;

  if (!email || !password) {
    alert("Please fill in all fields!");
    return;
  }

  try {
    const response = await fetch(`${API_URL}/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
    });

    const result = await response.json();

    if (response.ok) {
      if (!result.token || !result.userId) {
        throw new Error("Invalid response from server: token or userId missing.");
      }

      // Save token and userId in localStorage
      localStorage.setItem("token", result.token);
      localStorage.setItem("userId", result.userId);

      console.log("Login successful! Redirecting to home page...");
      window.location.href = "index.html";
    } else {
      throw new Error(result.message || "Login failed!");
    }
  } catch (error) {
    console.error("❌ Error during login:", error);
    alert(error.message);
  }
}

// Register Function
async function register() {
  const username = document.getElementById("username").value;
  const email = document.getElementById("email").value;
  const password = document.getElementById("password").value;

  if (!username || !email || !password) {
    alert("Please fill in all fields!");
    return;
  }

  try {
    const response = await fetch(`${API_URL}/register`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, email, password }),
    });

    const result = await response.text();

    if (response.ok) {
      alert("Registration successful! Redirecting to login page...");
      window.location.href = "login.html";
    } else {
      throw new Error(result || "Registration failed!");
    }
  } catch (error) {
    console.error("❌ Error during registration:", error);
    alert("Registration failed!");
  }
}

// Logout Function
function logout() {
  localStorage.removeItem("token");
  localStorage.removeItem("userId");
  window.location.href = "login.html";
}
