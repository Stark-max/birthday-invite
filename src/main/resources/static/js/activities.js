function playTruthOrDare(instanceId, guestId, choice) {
  fetch(`/activities/${instanceId}/play`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ guestId, choice })
  })
    .then((r) => r.json())
    .then((result) => {
      const card = document.getElementById(`tod-result-${instanceId}`);
      card.textContent = result.message;
      card.classList.add("flip");
      setTimeout(() => card.classList.remove("flip"), 350);
    });
}

function addTruthOrDarePrompt(event, instanceId, guestId) {
  event.preventDefault();
  const choice = document.getElementById(`tod-add-choice-${instanceId}`)?.value || "truth";
  const input = document.getElementById(`tod-add-prompt-${instanceId}`);
  const output = document.getElementById(`tod-add-result-${instanceId}`);
  const prompt = input?.value.trim() || "";
  fetch(`/activities/${instanceId}/play`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ guestId, action: "add", choice, prompt })
  })
    .then((r) => r.json())
    .then((result) => {
      if (output) output.textContent = result.message;
      if (result.success && input) input.value = "";
    });
}

function completeChallenge(instanceId, guestId, challengeIndex) {
  fetch(`/activities/${instanceId}/play`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ guestId, action: "complete", challengeIndex })
  })
    .then((r) => r.json())
    .then((result) => {
      const el = document.getElementById(`challenge-result-${instanceId}`);
      el.textContent = result.message;
    });
}

function drawMeme(instanceId, guestId) {
  fetch(`/activities/${instanceId}/play`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ guestId, action: "assign" })
  })
    .then((r) => r.json())
    .then((result) => {
      const output = document.getElementById(`challenge-result-${instanceId}`);
      const zone = document.getElementById(`meme-draw-${instanceId}`);
      if (output) output.textContent = result.message;
      if (result.success && zone) {
        zone.innerHTML = memeCardMarkup(result.data);
      }
    });
}

function submitMemeCaption(event, instanceId, guestId) {
  event.preventDefault();
  const input = document.getElementById(`meme-caption-${instanceId}`);
  const caption = input?.value.trim() || "";
  fetch(`/activities/${instanceId}/play`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ guestId, action: "submit", caption })
  })
    .then((r) => r.json())
    .then((result) => {
      const output = document.getElementById(`challenge-result-${instanceId}`);
      if (output) output.textContent = result.message;
      if (result.success && input) input.value = "";
    });
}

function voteChallenge(instanceId, guestId, targetResultId) {
  fetch(`/activities/${instanceId}/play`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ guestId, action: "vote", targetResultId })
  })
    .then((r) => r.json())
    .then((result) => {
      const el = document.getElementById(`challenge-result-${instanceId}`);
      el.textContent = result.message;
    });
}

function memeCardMarkup(data = {}) {
  const imageUrl = escapeHtml(data.memeImageUrl || "");
  const name = escapeHtml(data.memeName || "Мем");
  const region = escapeHtml(data.memeRegion || "global");
  const prompt = escapeHtml(data.memePrompt || "Придумай подпись");
  const accent = /^#[0-9a-fA-F]{6}$/.test(data.memeAccent || "") ? data.memeAccent : "#ffd166";
  const emoji = escapeHtml(data.memeEmoji || "😂");
  const media = imageUrl
    ? `<img src="${imageUrl}" alt="${name}">`
    : `<div class="meme-card-fallback"><span>${emoji}</span></div>`;
  return `
    <div class="meme-card" style="--meme-accent:${accent}">
      ${media}
      <div class="meme-card-body">
        <span class="meme-region">${region}</span>
        <strong>${name}</strong>
        <p>${prompt}</p>
      </div>
    </div>
  `;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}
