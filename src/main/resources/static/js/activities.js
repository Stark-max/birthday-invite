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

function drawMeme(buttonOrInstanceId, maybeInstanceId, maybeGuestId) {
  const hasButton = typeof buttonOrInstanceId === "object" && buttonOrInstanceId !== null;
  const button = hasButton ? buttonOrInstanceId : document.getElementById(`meme-spin-button-${buttonOrInstanceId}`);
  const instanceId = hasButton ? maybeInstanceId : buttonOrInstanceId;
  const guestId = hasButton ? maybeGuestId : maybeInstanceId;
  const output = document.getElementById(`challenge-result-${instanceId}`);
  const zone = document.getElementById(`meme-draw-${instanceId}`);

  if (button) {
    button.disabled = true;
    button.dataset.originalText = button.dataset.originalText || button.textContent;
    button.textContent = "Крутим...";
  }
  if (zone) {
    zone.innerHTML = memeRouletteMarkup();
    startMemeRoulette(zone);
  }

  fetch(`/activities/${instanceId}/play`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ guestId, action: "assign" })
  })
    .then((r) => r.json())
    .then((result) => {
      if (output) output.textContent = result.message;
      if (result.success && zone) {
        const finalSlot = zone.querySelector(".meme-reel-card-final");
        if (finalSlot) {
          finalSlot.innerHTML = memeReelCardContent(result.data);
        }
        setTimeout(() => {
          zone.innerHTML = memeCardMarkup(result.data);
          if (button) {
            button.textContent = "Мем выпал";
          }
        }, 1900);
      } else if (button) {
        button.disabled = false;
        button.textContent = button.dataset.originalText || "Крутить рулетку";
      }
    })
    .catch(() => {
      if (output) output.textContent = "Не удалось прокрутить рулетку. Попробуй ещё раз.";
      if (zone) zone.innerHTML = "";
      if (button) {
        button.disabled = false;
        button.textContent = button.dataset.originalText || "Крутить рулетку";
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
  const prompt = escapeHtml(data.memePrompt || "Повтори позу, эмоцию или сцену с картинки");
  const accent = /^#[0-9a-fA-F]{6}$/.test(data.memeAccent || "") ? data.memeAccent : "#ffd166";
  const emoji = escapeHtml(data.memeEmoji || "😂");
  const media = imageUrl
    ? `<img src="${imageUrl}" alt="${name}">`
    : `<div class="meme-card-fallback"><span>${emoji}</span></div>`;
  return `
    <div class="meme-card meme-assignment-card" style="--meme-accent:${accent}">
      ${media}
      <div class="meme-card-body">
        <span class="meme-task-badge">Тебе выпало</span>
        <span class="meme-region">${region}</span>
        <strong>${name}</strong>
        <p>${prompt}</p>
        <small class="muted">Повтори картинку на фото и отметь выполнение ниже.</small>
      </div>
    </div>
  `;
}

function memeRouletteMarkup() {
  const placeholders = [
    ["🖼", "выбираем"],
    ["🎬", "крутим"],
    ["📸", "ловим кадр"],
    ["✨", "почти"],
    ["🎭", "эмоция"],
    ["🧩", "шаблон"],
    ["🎲", "рандом"]
  ];
  const cards = placeholders
    .map(([emoji, label]) => `<div class="meme-reel-card"><span>${emoji}</span><small>${label}</small></div>`)
    .join("");
  return `
    <div class="meme-roulette">
      <div class="meme-roulette-window">
        <div class="meme-roulette-pointer"></div>
        <div class="meme-roulette-reel">
          ${cards}
          <div class="meme-reel-card meme-reel-card-final"><span>?</span><small>твой мем</small></div>
        </div>
      </div>
      <p class="muted">Рулетка выбирает свободный мем. Уже выпавшие картинки не участвуют в следующей выдаче.</p>
    </div>
  `;
}

function startMemeRoulette(zone) {
  const roulette = zone.querySelector(".meme-roulette");
  const viewport = zone.querySelector(".meme-roulette-window");
  const finalSlot = zone.querySelector(".meme-reel-card-final");
  if (!roulette || !viewport || !finalSlot) return;
  const stop = Math.round((viewport.clientWidth / 2) - (finalSlot.offsetLeft + finalSlot.offsetWidth / 2));
  roulette.style.setProperty("--meme-reel-stop", `${stop}px`);
  roulette.style.setProperty("--meme-reel-overrun", `${stop - 76}px`);
  requestAnimationFrame(() => roulette.classList.add("is-spinning"));
}

function memeReelCardContent(data = {}) {
  const imageUrl = escapeHtml(data.memeImageUrl || "");
  const name = escapeHtml(data.memeName || "Мем");
  const emoji = escapeHtml(data.memeEmoji || "🖼");
  const media = imageUrl ? `<img src="${imageUrl}" alt="${name}">` : `<span>${emoji}</span>`;
  return `${media}<small>${name}</small>`;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}
