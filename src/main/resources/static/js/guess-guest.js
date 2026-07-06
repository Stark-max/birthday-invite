const guessGuestStates = {};

document.addEventListener("DOMContentLoaded", () => {
  document.querySelectorAll(".guess-guest-root[data-instance-id]").forEach(initGuessGuestRoot);
});

function initGuessGuestRoot(root) {
  const instanceId = root.dataset.instanceId;
  const guestId = Number(root.dataset.guestId);
  const config = readActivityJson(`guess-guest-config-${instanceId}`);
  const results = readActivityJson(`guess-guest-results-${instanceId}`) || [];
  if (!config) return;
  const previous = results.find((result) => result.data?.type === "guess-guest");
  if (previous && config.allowRetry === false) {
    guessGuestStates[instanceId] = {
      guestId,
      config,
      rounds: Array.isArray(config.rounds) ? config.rounds : [],
      index: 0,
      answers: {}
    };
    renderGuessGuestResult(instanceId, {
      success: true,
      message: "Ты уже проходил(а) эту игру.",
      points: previous.points || 0,
      data: previous.data || {}
    });
  }
}

function startGuessGuest(instanceId, guestId) {
  const config = readActivityJson(`guess-guest-config-${instanceId}`);
  const root = document.getElementById(`guess-guest-root-${instanceId}`);
  if (!config || !root) return;
  const rounds = Array.isArray(config.rounds) ? config.rounds : [];
  if (!rounds.length) {
    root.innerHTML = "<p class=\"muted\">Игра пока не настроена.</p>";
    return;
  }
  guessGuestStates[instanceId] = {
    guestId,
    config,
    rounds,
    index: 0,
    answers: {}
  };
  renderGuessGuestRound(instanceId);
}

function selectGuessGuestAnswer(instanceId, roundId, guestId) {
  const state = guessGuestStates[instanceId];
  if (!state) return;
  state.answers[roundId] = Number(guestId);
  document.querySelectorAll(`[data-guess-option="${instanceId}-${roundId}"]`).forEach((button) => {
    button.classList.toggle("selected", Number(button.dataset.guestId) === Number(guestId));
  });
}

function nextGuessGuestRound(instanceId) {
  const state = guessGuestStates[instanceId];
  if (!state) return;
  const round = state.rounds[state.index];
  if (!state.answers[round.id]) {
    const output = document.getElementById(`guess-guest-message-${instanceId}`);
    if (output) output.textContent = "Выбери вариант ответа.";
    return;
  }
  state.index += 1;
  if (state.index >= state.rounds.length) {
    submitGuessGuestAnswers(instanceId);
    return;
  }
  renderGuessGuestRound(instanceId);
}

function submitGuessGuestAnswers(instanceId) {
  const state = guessGuestStates[instanceId];
  const root = document.getElementById(`guess-guest-root-${instanceId}`);
  if (!state || !root) return;
  root.innerHTML = "<p class=\"muted\">Проверяем ответы...</p>";
  fetch(`/activities/${instanceId}/play`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      guestId: state.guestId,
      type: "submit_answers",
      answers: state.answers
    })
  })
    .then((response) => response.json())
    .then((result) => renderGuessGuestResult(instanceId, result))
    .catch(() => {
      root.innerHTML = "<p class=\"muted\">Не удалось сохранить результат.</p>";
    });
}

function renderGuessGuestResult(instanceId, result) {
  const state = guessGuestStates[instanceId] || {};
  const root = document.getElementById(`guess-guest-root-${instanceId}`);
  if (!root) return;
  const data = result.data || {};
  const answers = Array.isArray(data.answers) ? data.answers : [];
  const rounds = Array.isArray(state.config?.rounds) ? state.config.rounds : [];
  const roundById = new Map(rounds.map((round, index) => [String(round.id), { ...round, index }]));
  const details = state.config?.showCorrectAnswer === false
    ? ""
    : answers.map((answer, index) => {
        const round = roundById.get(String(answer.roundId));
        const questionNumber = round ? round.index + 1 : index + 1;
        const clue = round?.clue ? `<p>${escapeHtml(round.clue)}</p>` : "";
        return `
          <div class="guess-result-row ${answer.correct ? "correct" : "wrong"}" data-round-id="${escapeHtml(answer.roundId)}">
            <div>
              <strong>Вопрос ${questionNumber}</strong>
              ${clue}
              ${answer.correctGuestName ? `<span>Правильный ответ: ${escapeHtml(answer.correctGuestName)}</span>` : ""}
            </div>
            <em>${answer.correct ? "Верно" : "Неверно"}</em>
          </div>
        `;
      }).join("");
  const total = Number(data.totalQuestions || answers.length || 0);
  const correct = Number(data.correctAnswers || 0);
  const percent = Number(data.percent || 0);
  root.innerHTML = `
    <div class="activity-result-card guess-result-summary">
      <div>
        <h4>${result.success ? "Готово" : "Не получилось"}</h4>
        ${result.success ? `<span class="guess-score-pill">${Number(result.points || 0)} очков</span>` : ""}
      </div>
      <p>${escapeHtml(result.message || "")}</p>
      ${result.success ? `<p>${correct} / ${total} · ${percent}%</p>` : ""}
      ${details ? `<div class="guess-result-list">${details}</div>` : ""}
      ${state.config?.showLeaderboard === false ? "" : `<button class="secondary" type="button" onclick="loadGuessGuestLeaderboard(${instanceId})">Показать лидерборд</button>`}
      <div id="guess-guest-leaderboard-${instanceId}"></div>
    </div>
  `;
}

function loadGuessGuestLeaderboard(instanceId) {
  fetch(`/activities/${instanceId}/leaderboard`)
    .then((response) => response.json())
    .then((items) => {
      const root = document.getElementById(`guess-guest-leaderboard-${instanceId}`);
      if (!root) return;
      root.innerHTML = `
        <div class="mini-leaderboard">
          ${items.map((item, index) => `
            <div><span>${index + 1}</span><strong>${escapeHtml(item.guestName)}</strong><em>${item.points} очков</em></div>
          `).join("")}
        </div>
      `;
    });
}

function renderGuessGuestRound(instanceId) {
  const state = guessGuestStates[instanceId];
  const root = document.getElementById(`guess-guest-root-${instanceId}`);
  if (!state || !root) return;
  const round = state.rounds[state.index];
  const options = guessGuestOptions(round, state.config);
  const progress = Math.round((state.index / state.rounds.length) * 100);
  root.innerHTML = `
    <div class="guess-progress"><span style="width:${progress}%"></span></div>
    <span class="muted">${state.index + 1} / ${state.rounds.length}</span>
    <h4>${escapeHtml(round.clue || "")}</h4>
    <div class="guess-options">
      ${options.map((option) => `
        <button type="button" class="secondary guess-option"
                data-guess-option="${instanceId}-${escapeHtml(round.id)}"
                data-guest-id="${option.id}"
                onclick="selectGuessGuestAnswer(${instanceId}, '${escapeJs(round.id)}', ${option.id})">
          ${escapeHtml(option.name)}
        </button>
      `).join("")}
    </div>
    <p class="muted" id="guess-guest-message-${instanceId}"></p>
    <button type="button" onclick="nextGuessGuestRound(${instanceId})">${state.index + 1 === state.rounds.length ? "Завершить" : "Дальше"}</button>
  `;
}

function guessGuestOptions(round, config) {
  const names = round.optionGuestNames || {};
  let ids = Array.isArray(round.optionGuestIds) ? round.optionGuestIds.slice() : [];
  if (!ids.length && round.answerGuestId) ids = [round.answerGuestId];
  let options = ids.map((id) => ({
    id: Number(id),
    name: names[String(id)] || `Гость ${id}`
  }));
  if (config.shuffleOptions !== false) {
    options = options.sort(() => Math.random() - 0.5);
  }
  return options;
}

function readActivityJson(id) {
  const el = document.getElementById(id);
  if (!el) return null;
  try {
    return JSON.parse(el.textContent);
  } catch (error) {
    return null;
  }
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function escapeJs(value) {
  return String(value).replaceAll("\\", "\\\\").replaceAll("'", "\\'");
}
