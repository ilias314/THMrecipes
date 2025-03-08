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
      localStorage.setItem("accessToken", result.accessToken);
      localStorage.setItem("refreshToken", result.refreshToken);
      localStorage.setItem("userId", result.userId);

      window.location.href = "recipes.html";
    } else {
      throw new Error(result.message || "Login failed!");
    }
  } catch (error) {
    console.error("❌ Error during login:", error);
    alert(error.message);
  }
}

// Refresh Token Function
async function refreshToken() {
  const refreshToken = localStorage.getItem("refreshToken");
  if (!refreshToken) {
    logout();
    return null;
  }

  try {
    const response = await fetch(`${API_URL}/refresh-token`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });

    const result = await response.json();

    if (response.ok) {
      localStorage.setItem("accessToken", result.accessToken);
      return result.accessToken;
    } else {
      logout();
      return null;
    }
  } catch (error) {
    console.error("Error refreshing token:", error);
    logout();
    return null;
  }
}

// Fetch API with Automatic Token Refresh
async function fetchWithAuth(url, options = {}) {
  let accessToken = localStorage.getItem("accessToken");

  if (!accessToken) {
    accessToken = await refreshToken();
    if (!accessToken) {
      return;
    }
  }

  options.headers = {
    ...options.headers,
    "Authorization": `Bearer ${accessToken}`
  };

  let response = await fetch(url, options);

  // If unauthorized, try refreshing the token
  if (response.status === 401) {
    accessToken = await refreshToken();
    if (!accessToken) return;

    options.headers["Authorization"] = `Bearer ${accessToken}`;
    response = await fetch(url, options);
  }

  return response;
}

// Logout Function
function logout() {
  localStorage.removeItem("accessToken");
  localStorage.removeItem("refreshToken");
  localStorage.removeItem("userId");
  window.location.href = "login.html";
}
